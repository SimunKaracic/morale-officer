def solution(n: Int): Int = {
  val binaryString = Integer.toBinaryString(n)
  val ones = binaryString.zipWithIndex.filter(_._1 == '1')
  var largestGap = 0

  if (ones.size == 1) {
    largestGap
  } else {
    ones.sliding(2)
      .map(onesAndIndices => onesAndIndices(1)._2 - (onesAndIndices(0)._2 + 1))
      .max
  }
}

solution(32)
