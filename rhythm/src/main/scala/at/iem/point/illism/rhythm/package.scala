package at.iem.point.illism

import spire.math.Rational

package object rhythm {
  implicit val numericCompat    = spire.math.compat.numeric   [Rational]  // Spire to Scala Numeric
  implicit val fractionalCompat = spire.math.compat.fractional[Rational]  // Spire to Scala Fractional
}
