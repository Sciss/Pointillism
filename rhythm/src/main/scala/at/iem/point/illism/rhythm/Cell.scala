/*
 *  Cell.scala
 *  (Pointillism)
 *
 *  Copyright (c) 2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 3, june 2007 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package at.iem.point.illism.rhythm

import spire.math._
import collection.immutable.{IndexedSeq => IIdxSeq}
import scala.annotation.tailrec
import spire.syntax._

object Cell {
  private val durationMap = Map(
    1 -> "64", /* r"3/2" -> "64.", */ 2 -> "32", 3 -> "32.", 4 -> "16", 6 -> "16.", 8 -> "8",
    12 -> "8.", 16 -> "4", 24 -> "4.", 32 -> "2", 48 -> "2.", 64 -> "1", 96 -> "1."
  )

  private def lilyDurations(dur: Int): IIdxSeq[String] = {
    if (dur > 96) {
      return durationMap(96) +: lilyDurations(dur - 96)
    }
 		// require(dur <= 96, s"Rhythmic value $dur exceeds 96")
 		durationMap.get(dur) match {
      case Some(single) => Vector(single)
      case _ =>
        // e.g. 13 = 8 + 4 + 1
        val c = (6 to 0 by -1).map(1 << _).collect {
          case i if (dur & i) != 0 => i
        }
        c.map(durationMap)
    }
 	}
}
final case class Cell(id: Int, elements: IIdxSeq[NoteOrRest], dur: Rational) {
  override def toString = s"Cell#$id($prettyElements}, dur = $dur)"

  /** Number of elements in the cell */
  def size = elements.size

  /** Pretty formatted string representation of the cell's elements */
  def prettyElements: String = elements.map(_.toNumber).mkString("[", ", ", "]")

  /** Pretty formatted string representation of the cell */
  def pretty: String = {
    val s = f"#$id%2s: $prettyElements, ${dur.toString}" // "${dur.toString}%3s"
    s + (" " * math.max(0, 40 - s.length))
  }

  /** Multiplies the elements by a factor so that their sum will become the nominal total duration. */
  def normalized: Cell = {
    val factor = dur / elements.map(_.dur).sum
    copy(elements = elements.map(_ * factor) /* , dur = 1 */)
  }

  /** Scales the total duration of the cell by a given factor */
  def * (factor: Rational): Cell = copy(dur = dur * factor)

  /** 'Unnormalizes' this cell, so that the elements are all integers, possibly multiplying
    * them by the least common multiple.
    */
  def usingIntegers: Cell = {
    val durs    = elements.map(_.dur)
    val denoms  = durs.map(_.denominator)
    val k       = denoms.reduce(lcm(_, _))
    if (k == 1) return this
    val elemsM  = elements.map(_ * k)
    copy(elements = elemsM)
  }

  private[Cell] def factor: Int = {
    val sum   = elements.map(_.dur).sum
    val dur64 = dur * 64

    @tailrec def loop(f: Int): Int = if (sum * f < dur64) loop(f << 1) else f

    loop(1)
  }

  private[Cell] def adjusted: IIdxSeq[NoteOrRest] = {
    val f = factor
    elements.map(_ * f)
  }

  private[Cell] def tuplet: Rational = {
    val adj   = adjusted
    val dur64 = dur * 64
    val r1    = adj.map(_.dur).sum / dur64

    @tailrec def loop(r: Rational): Rational = if (r <= 2) r else loop(r / 2)

    loop(r1)
  }

  private[Cell] def hasTuplet: Boolean = tuplet != 1

  /** Produces a string representation of this cell, suitable for rendering with Lilypond.
    * For example, a cell with elements (12, 5, 11) and duration of 21/32, will result in the following string:
    *
    * {{
    *     \time 21/32
    *     \times 3/4 { c'4.  c'8 ~ c'32  c'4 ~ c'16 ~ c'32 }
    * }}
    *
    * (This is mostly adapted from LilyCollider)
    *
    *
    * @return
    */
  def toLilypondString(timeSig: Boolean = true, annotation: String = ""): String = {
    val cell      = this.usingIntegers
    val ns0       = cell.adjusted.map { n =>
      val d = n.dur
      val i = d.toInt
      assert(d == i, d)
      val s = if (n.isRest) "r" else "c'"
      Cell.lilyDurations(i).map(ds => s"$s$ds").mkString(" ~ ")
    }
    val ns  = if (annotation == "") ns0 else {
      val annotStr = raw"""\annotation "$annotation" """
      ns0 match {
        case head +: tail => head +: annotStr +: tail
        case _ => ns0 :+ annotStr
      }
    }
    val nss = ns.mkString(" ")
    val tup = if (cell.hasTuplet) {
      s"\\times ${cell.tuplet.reciprocal} { $nss }"
    } else {
      nss
    }

    if (timeSig) {
      s"\\time ${cell.dur} \n$tup\n"
    } else {
      tup
    }
  }
}