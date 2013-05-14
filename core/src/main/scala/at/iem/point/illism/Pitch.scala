/*
 *  Pitch.scala
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

package at.iem.point.illism

object Pitch {
  // cf. http://www.sengpielaudio.com/Rechner-notennamen.htm

  implicit val ordering = Ordering.by[Pitch, Int](_.midi)

  def toString(midi: Int, lang: Language = Language.default): String = {
    val pc        = midi % 12
    val register  = midi / 12
    lang match {
      case Language.English =>
        PitchClass.toString(pc, lang) + register
      case Language.German =>
        val caps  = register <= 3
        val pcs   = PitchClass.toString(pc, lang, caps)
        if (register <= 2) {
          val ticks = 3 - register
          ("," * ticks) + pcs
        } else if (register >= 5) {
          val ticks = register - 4
          pcs + ("'" * ticks)
        } else {
          pcs
        }
    }
 	}
}
final class Pitch(val midi: Int) extends AnyVal {
  override def toString = Pitch.toString(midi)

  def interval(that: Pitch): DirectedInterval = (this, that) // new DirectedInterval(math.abs(this.midi - that.midi))

  def map(fun: Int => Int): Pitch = new Pitch(fun(midi))

  implicit def `class`: PitchClass = new PitchClass(midi % 12)
}

object PitchClass {
  private final val pcStrings_en = Array("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
  private final val pcStrings_de = Array("c","cis","d","dis","e","f","fis","g","gis","a","ais","h")

  def toString(step: Int, lang: Language = Language.default, caps: Boolean = false): String = {
    lang match {
      case Language.English => pcStrings_en(step)
      case Language.German =>
        val s = pcStrings_de(step)
        if (caps) s.capitalize else s
    }
 	}

}
final class PitchClass(val step: Int) extends AnyVal {
  override def toString = PitchClass.toString(step)
}
