/*
 *  NoteUtil.scala
 *  (Pointillism)
 *
 *  Copyright (c) 2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
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

import de.sciss.midi
import midi.{Track, TickRate}
import collection.immutable.{IndexedSeq => IIdxSeq}
import annotation.tailrec

object NoteUtil {
//  def findMonophones(in: IIdxSeq[OffsetNote], overlapTolerance: Double = 0.1): IIdxSeq[IIdxSeq[OffsetNote]] = {
//    ???
//  }

  def toTrack(notes: IIdxSeq[OffsetNote], channel: Int = 0)(implicit tickRate: TickRate): Track = {
    val events = notes.flatMap(_.toMIDI(channel))
    Track(events)
  }

  /** Cleans a list of notes by throwing away those under a given minimum duration,
    * and adjusting the others to match note beginnings and endings within this
    * minimum duration window.
    */
  def clean(notes: IIdxSeq[OffsetNote], minDuration: Double = 0.1): IIdxSeq[OffsetNote] = {
    if (notes.isEmpty) return Vector.empty

    val tAll = notes.filter(_.duration >= minDuration)
    var tFlt = Vector.empty[OffsetNote]
    tAll.iterator.foreach { n =>
      val start   = n.offset
      val minStop = start + minDuration
      tFlt      = tFlt.collect {
        // no overlap, keep as is
        case n1 if n1.stop <= start => n1

        // overlaps, but begins early enough
        case n1 if start - n1.offset >= minDuration =>
          if (n1.stop >= minStop) n1 else {
            n1.replaceStop(start) // truncate end to align with current note's offset
          }

        // overlaps, but ends late enough
        case n1 if n1.stop >= minStop =>
          if (start - n1.offset >= minDuration) n1 else {
            n1.replaceStart(start) // truncate beginning to align with current note's offset
          }
      }
      tFlt :+= n
    }
    tFlt
  }

  def stabbings(notes: IIdxSeq[OffsetNote]): IIdxSeq[Double] = {
    notes.flatMap(n => n.offset :: n.stop :: Nil).toSet.toIndexedSeq.sorted
  }

  def splitMelodicHarmonic(notes: IIdxSeq[OffsetNote], minChordDuration: Double = 0.1, minSeq: Int = 2):
    (IIdxSeq[((Double, Double), Vector[OffsetNote])], IIdxSeq[((Double, Double), Vector[Chord])]) = {

    var resSeq      = Vector.empty[((Double, Double), Vector[OffsetNote])]
    var resChords   = Vector.empty[((Double, Double), Vector[Chord])]
    if (notes.isEmpty) return (resSeq, resChords)

    // the cleaning is only used for generating the stabbing points
    val tFlt    = NoteUtil.clean(notes, minDuration = minChordDuration)
    val stabs   = NoteUtil.stabbings(tFlt)
    var chords  = Vector.empty[Chord]
    val pairs   = stabs.sliding(2, 1)

    // initially put all notes into the sequential bin, then
    // as chords are detected remove them from this set
    var seqSet  = Set(notes:_ *)

    pairs.foreach {
      case IIdxSeq(start, stop) =>
        val par = notes.filter { n =>
          n.offset - minChordDuration <= start && n.stop + minChordDuration >= stop
        }
        if (par.size >= 2) {
          val segm = par.map { n0 =>
            val n1 = if (start - n0.offset > minChordDuration) n0.replaceStart(start) else n0
            val n2 = if (n1.stop - stop    > minChordDuration) n1.replaceStop (stop ) else n1
            n2
          }
          chords :+= Chord(segm.sortBy(_.pitch))
          seqSet --= par
        }
    }

    val seq   = seqSet.to[Vector].sortBy(_.offset)
    chords    = chords.sortBy(_.minOffset)

    var seqIdx      = 0
    var chordIdx    = 0
    var seqSpan     = Vector.empty[OffsetNote]
    var chordSpan   = Vector.empty[Chord]

    def isExhausted = seqIdx == seq.size || chordIdx == chords.size
    def isHoriz     = seq(seqIdx).offset < chords(chordIdx).minOffset

    def flush() {
      if (seqSpan.nonEmpty) {
        resSeq :+= ((seqSpan.head.offset, seqSpan.map(_.stop).max), seqSpan)
        seqSpan  = Vector.empty
      }
      if (chordSpan.nonEmpty) {
        resChords :+= ((chordSpan.head.minOffset, chordSpan.map(_.maxStop).max), chordSpan)
        chordSpan   = Vector.empty
      }
    }

    @tailrec def horizontal() {
      val n     = seq(seqIdx)
      seqIdx   += 1
      seqSpan :+= n
      if (!isExhausted) {
        if (isHoriz) horizontal()
        else {
          flush()
          vertical()
        }
      }
    }

    @tailrec def vertical() {
      val n       = chords(chordIdx)
      chordIdx   += 1
      chordSpan :+= n
      if (!isExhausted) {
        if (!isHoriz) vertical()
        else {
          flush()
          horizontal()
        }
      }
    }

    if (!isExhausted) { if (isHoriz) horizontal() else vertical() }
    flush()

    (resSeq, resChords)
  }
}