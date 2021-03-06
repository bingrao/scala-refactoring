package scala.tools.refactoring.tests.util

import org.junit.Test
import org.junit.Assert._
import scala.tools.refactoring.util.SourceWithMarker
import scala.tools.refactoring.util.SourceWithMarker.Movements
import scala.tools.refactoring.util.SourceWithMarker.Movement
import scala.tools.refactoring.util.SourceWithMarker.MovementHelpers

class SourceWithMarkerTest {
  import SourceWithMarker.Movements._

  @Test
  def testApplyCharMovemnts(): Unit = {
    val src = SourceWithMarker("implicit")
    assertEquals('m', src.moveMarker('i').current)
    assertEquals('l', src.moveMarker('u' | ('i' ~ 'm' ~ 'p')).current)
    assertEquals('i', src.moveMarker('i' ~ 'm'.backward).current)
  }

  @Test
  def testApplyCharAndStringMovements(): Unit = {
    val src = SourceWithMarker("abstract")
    assertEquals('a', src.moveMarker("").current)
    assertEquals('a', src.moveMarker(('a' ~ "bs") ~ ('b' ~ "str").backward).current)
    assertTrue(src.moveMarker("abstrac" ~ "abstract".backward).isDepleted)
  }

  @Test
  def testApplyBasicMovements(): Unit = {
    val src = SourceWithMarker("protected abstract override val x = 123")
    assertEquals('=', src.moveMarker((("protected" | "abstract" | "override" | "val" | "x" | "=") ~ spaces).zeroOrMore ~ "12" ~ (spaces ~ "123").backward).current)
  }

  @Test
  def testCommentsAndSpaces(): Unit = {
    val src1 = SourceWithMarker("""//--
      val x = 3
    """)

    val src2 = SourceWithMarker("/**/val x = 3")

    val src3 = SourceWithMarker("""
      //--
      //--/**/------------->>
      //--

      /*
       * /**/
       */
      val test = 3
    """)

    val src4 = SourceWithMarker("x//", 2)

    val srcStr5 = "/**/ //**/"
    val src5 = SourceWithMarker(srcStr5, srcStr5.size - 1)

    assertEquals("x", src4.moveMarker(commentsAndSpaces.backward).currentStr)
    assertEquals("v", src1.moveMarker(commentsAndSpaces).currentStr)
    assertEquals("v", src2.moveMarker(commentsAndSpaces).currentStr)
    assertEquals("v", src3.moveMarker(commentsAndSpaces).currentStr)
    assertTrue(src5.moveMarker(commentsAndSpaces.backward).isDepleted)
  }

  @Test
  def testSpaces(): Unit = {
    val src1 = SourceWithMarker(" s")
    val src2 = SourceWithMarker("\n\ts")
    val src3 = SourceWithMarker("""
      s    s                s
    """)

    assertEquals("s", src1.moveMarker(spaces).currentStr)
    assertEquals("s", src2.moveMarker(spaces).currentStr)
    assertEquals("s", src3.moveMarker(spaces ~ 's' ~ spaces ~ 's' ~ spaces).currentStr)
    assertTrue(src1.moveMarker(spaces ~ (spaces ~ "s").backward).isDepleted)
  }

  @Test
  def testApplyWithMoreComplexExample(): Unit = {
    val src = SourceWithMarker("""protected //--
      [test /**/]//--
      /*
       /*
        /**/
        */
      */ override val test = 99""")

    val moveToBracketOpen = "protected" ~ commentsAndSpaces
    val moveToBracketClose = moveToBracketOpen ~ bracketsWithContents ~ '/'.backward
    val moveToStartOfMultilineComment = moveToBracketClose ~ ']' ~ (comment) ~ spaces
    val moveToEndOfMultilineComment = moveToStartOfMultilineComment ~ commentsAndSpaces ~ (spaces ~ 'o').backward
    val moveToVal = moveToBracketClose ~ ']' ~ commentsAndSpaces ~ "override" ~ commentsAndSpaces

    assertEquals("[", src.moveMarker(moveToBracketOpen).currentStr)
    assertEquals("]", src.moveMarker(moveToBracketClose).currentStr)
    assertEquals("/", src.moveMarker(moveToStartOfMultilineComment).currentStr)
    assertEquals("/", src.moveMarker(moveToEndOfMultilineComment).currentStr)
    assertEquals("v", src.moveMarker(moveToVal).currentStr)

    assertTrue(src.moveMarker("protected" ~ ("protected" ~ commentsAndSpaces).backward).isDepleted)
    assertTrue(src.moveMarker(moveToVal ~ (moveToVal ~ "v").backward).isDepleted)
  }

  @Test
  def testWithRealisticExamples(): Unit = {
    val srcStr = """
      package bug
      class Bug {
        private/*--*/ //**//**//**//**//**/
        // -/**/-
        // -/**/-
        [/**/ bug /**/] val /*(*/z/*)*/ = 99
      }
      """

    val src = SourceWithMarker(srcStr, srcStr.lastIndexOf("]"))
    val mvmt = (("private" | "protected") ~ commentsAndSpaces ~ bracketsWithContents).backward

    assertEquals("p", src.moveMarker(mvmt ~ commentsAndSpaces).currentStr)
  }

  @Test
  def testWithScopedAccessModifiers(): Unit = {
    val src = SourceWithMarker("private[test]").withMarkerAtLastChar
    assertTrue(src.moveMarker((("private" | "protected") ~ commentsAndSpaces ~ bracketsWithContents).backward).isDepleted)
  }

  val mvntsToTestAtEndOfString = {
    val mvnts =
      characterLiteral ::
      plainid ::
      op ::
      opChar ::
      stringLiteral ::
      comment ::
      comment.atLeastOnce ::
      bracket ::
      bracketsWithContents ::
      charToMovement('c') ::
      stringToMovement("s") ::
      symbolLiteral ::
      any ::
      none ::
      any.butNot('c') ::
      space ::
      Nil

    mvnts.zipWithIndex.map { case (m, i) => (m, s"mvnts[$i]") }
  }

  @Test
  def testWithEmptySource(): Unit = {
    mvntsToTestAtEndOfString.foreach { case (mvnt, indexStr) =>
      try {
        assertTrue(indexStr, mvnt(SourceWithMarker()).isEmpty)
      } catch {
        case e: IllegalArgumentException =>
          throw new RuntimeException(s"Error at $indexStr", e)
      }
    }
  }

  @Test
  def testAtEndOfSource(): Unit = {
    val src = SourceWithMarker("a")
    val prefixMvnt = stringToMovement("a")

    mvntsToTestAtEndOfString.foreach { case (mvnt, indexStr) =>
      assertTrue(indexStr, src.moveMarker(prefixMvnt ~ mvnt.zeroOrMore).isDepleted)
      assertTrue(indexStr, src.moveMarker((mvnt ~ prefixMvnt.zeroOrMore) | prefixMvnt.zeroOrMore).isDepleted)
    }
  }

  @Test
  def testCharConst(): Unit = {
    val src1 = SourceWithMarker("(a='b',b=''',c='\'',e='s)")
    val baseMvnt1 = '(' ~ "a=" ~ characterLiteral ~ ",b=" ~ characterLiteral ~ ",c=" ~ characterLiteral ~ ",e="

    assertEquals("'", src1.moveMarker(baseMvnt1).currentStr)
    assertEquals("(", src1.moveMarker(baseMvnt1 ~ characterLiteral).currentStr)
    assertTrue(src1.moveMarker(baseMvnt1 ~ "'s)").isDepleted)

    val src2 = SourceWithMarker("""'\222'x''';'\b'°'\\'''\"'::""")
    val baseMvnt2 = (characterLiteral ~ any).zeroOrMore

    assertEquals(":", src2.moveMarker(baseMvnt2).currentStr)
    assertEquals("'", src2.moveMarker(baseMvnt2 ~ (any ~ characterLiteral ~ any).backward).currentStr)
  }

  @Test
  def testNtimes(): Unit = {
    val src = SourceWithMarker("aaaa")
    assertTrue(src.moveMarker('a'.nTimes(4)).isDepleted)
    assertFalse(src.moveMarker('a'.nTimes(3)).isDepleted)
    assertFalse(src.moveMarker('a'.nTimes(5)).isDepleted)
  }

  @Test
  def testOpChar(): Unit = {
    val src = SourceWithMarker("+-/*:><!~^\u03f6.")
    assertEquals("~", src.moveMarker(opChar.zeroOrMore ~ (opChar.nTimes(2) ~ '.').backward).currentStr)
  }

  @Test
  def testUntilWithSimpleExamples(): Unit = {
    val src = SourceWithMarker("0123456789")
    assertEquals("5", src.moveMarker(until("5")).currentStr)
    assertEquals("5", src.moveMarker(until("5", skipping = digit)).currentStr)
    assertEquals("0", src.moveMarker(until("5", skipping = digit.zeroOrMore)).currentStr)
    assertEquals("1", src.moveMarker("01234" ~ until('1', skipping = '2').backward).currentStr)
  }

  @Test
  def testStringLiteral(): Unit = {
    val trippleQuote = "\"\"\""

    val src1 = SourceWithMarker(s"""$trippleQuote a $trippleQuote""")
    assertTrue(src1.moveMarker(stringLiteral).isDepleted)

    val src2 = SourceWithMarker(raw"""
      $trippleQuote
      asdfasdfadsf
      ""
      asdfasdf
      $trippleQuote
      "asdf\""
      "\n"
      "\t\"\"";
    """)

    val mvnt2 = (spaces ~ stringLiteral ~ spaces).atLeastOnce
    assertEquals(";", src2.moveMarker(mvnt2).currentStr)
  }

  @Test
  def testUntilWithTypicalExamples(): Unit = {
    def untilVal(name: String) = until(name ~ spaces ~ "=", skipping = characterLiteral | symbolLiteral | stringLiteral | comment)

    val src1 = SourceWithMarker("(j = i, i = j)")
    val src2 = SourceWithMarker("""(j = "i = 2;", i = "v = 2"/*k=2*/,k=l)""")

    val trippleQuote = "\"\"\""
    val src3 = SourceWithMarker(s"""(a = $trippleQuote
      b = 0
      c = 0
      e = 0
      $trippleQuote, b = 'd_=, c = 'd', d= 3)
    )""")

    assertEquals("i", src1.moveMarker(untilVal("j") ~ any.nTimes(4)).currentStr)
    assertEquals("j", src1.moveMarker(untilVal("i") ~ any.nTimes(4)).currentStr)

    assertEquals("v", src2.moveMarker(untilVal("i") ~ any.nTimes(5)).currentStr)
    assertEquals("l", src2.moveMarker(untilVal("k") ~ any.nTimes(2)).currentStr)

    assertEquals("3", src3.moveMarker(untilVal("d") ~ any.nTimes(3)).currentStr)

    assertEquals(")", src1.moveMarker(until(')')).currentStr)
    assertEquals("(", src1.moveMarker(until("xxx", any)).currentStr)

    assertEquals("(", src1.moveMarker(until(')')).moveMarkerBack(until('(', skipping = id | '=' | space | comment)).currentStr)
    assertEquals(")", src1.moveMarker(until(')')).moveMarkerBack(until(')', skipping = '=' | space | comment)).currentStr)

    val src4 = SourceWithMarker("(a = { 1 + * (3 + 4) }, b = 9)")
    assertEquals("3", src4.moveMarker(
        until(')') ~ until('(').backward ~ '(').currentStr)

    assertEquals(")", src4.moveMarker(
        until(')') ~ until('(').backward ~ "(3" ~ until(')')).currentStr)

    assertEquals("=", src4.moveMarker(
        until(')') ~ until('(').backward ~ "(3" ~ until(')', skipping = "4)") ~ until("(", skipping = curlyBracesWithContents).backward ~ "(a ").currentStr)

    assertTrue(SourceWithMarker("chainMe(").moveMarker(id ~ '(').isDepleted)
    assertTrue(SourceWithMarker("chainMe").withMarkerAtLastChar.moveMarkerBack(Movements.letter.atLeastOnce).isDepleted)

    val src5 = SourceWithMarker("chainMe(a = 2).chainMe(b = 9, xxx = 2)")

    assertEquals("9", src5.moveMarker(until("9")).currentStr)

    assertEquals("(",
        src5.moveMarker(
            until("9") ~ until('(', skipping = (curlyBracesWithContents | comment)).backward).currentStr)

    assertEquals("b",
        src5.withMarkerAtLastChar.moveMarker(
            until(Movements.id ~ commentsAndSpaces ~ '(', skipping = (curlyBracesWithContents | comment)).backward ~ '(').currentStr)
  }

  @Test
  def testUntilWithNonConsumingSkippingMovement(): Unit = {
    val src = SourceWithMarker("^123[^trap$]456$")

    val mvnt =
      until('$', skipping = bracketsWithContents.zeroOrMore) ~
      "6$".backward ~
      until('^', skipping = bracketsWithContents.zeroOrMore).backward ~
      "^1"

    assertEquals("2", src.moveMarker(mvnt).currentStr)
  }

  @Test
  def testSeqOpsAtEndOfSource(): Unit = {
    val src = SourceWithMarker("aaa")

    val shouldDeplete =
      ('a' ~ 'a' ~ 'a') ::
      ('a'.atLeastOnce) ::
      ('a'.zeroOrMore) ::
      (any ~ any ~ any) ::
      (('a' ~ 'a' ~ 'a' ~ 'a').zeroOrMore ~ 'a'.atLeastOnce) ::
      (('a' ~ 'a' ~ 'a') ~ 'a'.zeroOrMore) ::
      (('a' ~ 'a' ~ 'a' ~ 'a') | 'a'.nTimes(3)) ::
      Nil

    val shouldNotDeplete =
      ('a' ~ 'a' ~ 'a' ~ 'a') ::
      (('a' ~ 'a' ~ 'a') ~ 'a'.atLeastOnce) ::
      Nil

   shouldDeplete.foreach { mvnt =>
      assertTrue(src.moveMarker(mvnt).isDepleted)
    }

   shouldNotDeplete.foreach { mvnt =>
      assertFalse(src.moveMarker(mvnt).isDepleted)
    }
  }

  private def runCoveredStringTest(input: String, start: Int, mvnt: Movement, shouldBeCovered: String, forwardOnly: Boolean = false): Unit = {
    val srcWithMarkerWhenForward = SourceWithMarker(input, start)
    assertEquals(shouldBeCovered, Movement.coveredString(srcWithMarkerWhenForward, mvnt))

    if (!forwardOnly) {
      val srcWithMarkerWhenBackward = SourceWithMarker(input, start + math.max(0, shouldBeCovered.size - 1))
      assertEquals(shouldBeCovered, Movement.coveredString(srcWithMarkerWhenBackward, mvnt.backward))
    }
  }

  @Test
  def testCoveredString(): Unit = {
    runCoveredStringTest("", 0, any, "")
    runCoveredStringTest("0", 0, any, "0")
    runCoveredStringTest("1", 0, any.backward, "1")
    runCoveredStringTest("1223", 1, '2'.zeroOrMore, "22")
  }

  @Test
  def testScalaId(): Unit = {
    def runSimpleIdTest(input: String, shouldBeCovered: String, forwardOnly: Boolean = false) = runCoveredStringTest(input, 0, Movements.id, shouldBeCovered, forwardOnly)

    runSimpleIdTest("", "")
    runSimpleIdTest("x", "x")
    runSimpleIdTest("privateval", "privateval")
    runSimpleIdTest("val x = 3", "", forwardOnly = true)
    runSimpleIdTest("->", "->")
    runSimpleIdTest("2", "")
    runSimpleIdTest("x2", "x2")
    runSimpleIdTest("/*comment*/", "", forwardOnly = true)
    runSimpleIdTest("//comment", "", forwardOnly = true)
    runSimpleIdTest("::", "::")
    runSimpleIdTest("::/", "::/")
    runSimpleIdTest("::/*trap*/", "::")
    runSimpleIdTest("::/*<-trap*/", "::")
    runSimpleIdTest("+", "+")
    runSimpleIdTest("+/", "+/")
    runSimpleIdTest("+//trap", "+")
    runSimpleIdTest("+//<-trap", "+")
    runSimpleIdTest("+/*trap*/", "+")
    runSimpleIdTest("+/*<-trap*/", "+")
    runSimpleIdTest("_id", "_id")
    runSimpleIdTest("id_id", "id_id")
    runSimpleIdTest("_id_id_id", "_id_id_id")
    runSimpleIdTest("dot_product_*", "dot_product_*")
    runSimpleIdTest("__system", "__system")

    val srcIdInMlComment = SourceWithMarker("/*::*/", 3)
    val srcIdInSlComment = SourceWithMarker("//::", 3)

    assertEquals(srcIdInMlComment, srcIdInMlComment.moveMarkerBack(Movements.id))
    assertEquals(srcIdInSlComment, srcIdInSlComment.moveMarkerBack(Movements.id))
  }

  @Test
  def testScalaOpChar(): Unit = {
    def runOpCharTest(input: String, shouldBeCovered: String) = runCoveredStringTest(input, 0, Movements.opChar.atLeastOnce, shouldBeCovered)

    runOpCharTest("+", "+")
    runOpCharTest("-", "-")
    runOpCharTest(":", ":")
  }

  @Test
  def testRefineMovement(): Unit = {
    val expressionWithoutRefinement = {
      (digit.atLeastOnce || ("(" || ")" || "*" || "+") || space).zeroOrMore
    }

    val expressionWithRefinement = expressionWithoutRefinement.refineWith { (sourceWithMarker, positions) =>
      positions.filter { pos =>
        val coveredChars = MovementHelpers.coveredChars(sourceWithMarker, pos)
        val nOpenParen = coveredChars.count(_ == '(')
        val nCloseParen = coveredChars.count(_ == ')')
        nOpenParen == nCloseParen
      }
    }

    assertEquals(Some(0), expressionWithoutRefinement(SourceWithMarker("")))
    assertEquals(Some(0), expressionWithRefinement(SourceWithMarker("")))

    assertEquals("#", SourceWithMarker("()#").moveMarker(expressionWithRefinement).currentStr)
    assertEquals("#", SourceWithMarker("(#").moveMarker(expressionWithoutRefinement).currentStr)
    assertEquals("(", SourceWithMarker("(#").moveMarker(expressionWithRefinement).currentStr)
    assertEquals("#", SourceWithMarker("1 + (2#").moveMarker(expressionWithoutRefinement).currentStr)
    assertEquals("(", SourceWithMarker("1+(2#").moveMarker(expressionWithRefinement).currentStr)
    assertEquals("#", SourceWithMarker("1+(2+(2+2))#").moveMarker(expressionWithRefinement).currentStr)

    assertEquals("#", SourceWithMarker("#1+1").withMarkerAtLastChar.moveMarkerBack(expressionWithRefinement).currentStr)
    assertEquals("(", SourceWithMarker("#(2+1").withMarkerAtLastChar.moveMarkerBack(expressionWithRefinement).currentStr)
    assertEquals("#", SourceWithMarker("#(2+1)+1").withMarkerAtLastChar.moveMarkerBack(expressionWithRefinement).currentStr)
  }

  @Test
  def testCurlyBracesWithContents(): Unit = {
    val src = SourceWithMarker(""">
    val x1 = {
      val x2 = {
         3
      }
      //; }
      //; {
      /*{*/
      x + 2
      /*}*/
    };
    """)

    assertEquals(";", src.moveMarker('>' ~ commentsAndSpaces ~ "val x1 = " ~ curlyBracesWithContents).currentStr)

    val res = src.applyMovement(until(";", skipping = curlyBracesWithContents)).flatMap { src =>
      src.applyMovement(until("x", skipping = curlyBracesWithContents).backward).flatMap { src =>
        src.applyMovement("x1 = ")
      }
    }

    assertTrue(res.isDefined)
  }

  @Test
  def testGoingBackward(): Unit = {
    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.moveMarkerBack("abc").isDepleted)
    }

    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.withMarkerAtLastChar.moveMarkerBack("x" | "y" | "abc").isDepleted)
    }

    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.withMarkerAtLastChar.moveMarkerBack("a" ~ "b" ~ "c").isDepleted)
    }

    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.withMarkerAtLastChar.moveMarkerBack("a".atLeastOnce ~ "b".zeroOrMore ~ "c".atLeastOnce).isDepleted)
    }

    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.withMarkerAtLastChar.moveMarkerBack("a".zeroOrMore ~ "b".atLeastOnce ~ "c".zeroOrMore).isDepleted)
    }

    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.withMarkerAtLastChar.moveMarkerBack((character('a') | 'c').butNot('d') ~ 'b' ~ 'c').isDepleted)
    }

    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.withMarkerAtLastChar.moveMarkerBack('a' ~ 'b' ~ 'c').isDepleted)
    }

    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.withMarkerAtLastChar.moveMarkerBack("abc".butNot("ab" | "bc")).isDepleted)
    }

    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.withMarkerAtLastChar.moveMarkerBack(idrest).isDepleted)
    }

    {
      val src = SourceWithMarker("abc").withMarkerAtLastChar
      assertTrue(src.withMarkerAtLastChar.moveMarkerBack(varid).isDepleted)
    }
  }

  @Test
  def testWithSimpleExamplesWhereBacktrackingIsNeeded(): Unit = {
    {
      val src = SourceWithMarker("aaaab")
      assertTrue(src.moveMarker('a'.zeroOrMore ~ 'a' ~ 'b').isDepleted)
      assertTrue(src.moveMarker('a'.zeroOrMore ~ 'a'.nTimes(4) ~ 'b').isDepleted)
    }

    {
      val src = SourceWithMarker("aaaab").withMarkerAtLastChar
      val asOrBs = ('a'.zeroOrMore || 'b'.zeroOrMore)
      assertTrue(src.moveMarkerBack(asOrBs ~ 'a' ~ 'b').isDepleted)
      assertTrue(src.moveMarkerBack(asOrBs.zeroOrMore ~ asOrBs.nTimes(4) ~ 'b').isDepleted)
    }

    {
      val src = SourceWithMarker("aaab")
      val mvnt = 'a' ~ 'a'.atLeastOnce
      assertTrue(src.moveMarker(mvnt ~ (mvnt ~ 'b').backward ~ mvnt ~ 'b').isDepleted)
    }
  }

  @Test
  def testZeroOrMore(): Unit = {
    {
      val src = SourceWithMarker("aaa")
      assertEquals(Seq(3, 2, 1, 0), 'a'.zeroOrMore.compute(src))
    }

    {
      val src = SourceWithMarker("")
      assertTrue(src.applyMovement('a'.zeroOrMore).isDefined)
    }

    {
      val src = SourceWithMarker("a")
      assertTrue(src.moveMarker('a'.zeroOrMore).isDepleted)
    }

    {
      val src = SourceWithMarker("aaaa")
      assertTrue(src.moveMarker('a'.zeroOrMore).isDepleted)
    }

    {
      val src = SourceWithMarker("aaaa")
      assertTrue(src.moveMarker(('a' || 'b').zeroOrMore).isDepleted)
    }

    {
      val src = SourceWithMarker("aaaa")
      assertTrue(src.moveMarker('a'.zeroOrMore.zeroOrMore).isDepleted)
    }

    {
      val src = SourceWithMarker("abaabbbaaabaaabaa")
      assertTrue(src.moveMarker(('a' ~ 'b'.zeroOrMore ~ 'a'.zeroOrMore).zeroOrMore).isDepleted)
    }
  }

  @Test
  def testMakeSureThatSourceWithMarkerOrIsGreedy(): Unit = {
    def performTest(toConsume: String, mvnt: Movement): Unit = {
      runCoveredStringTest(toConsume, 0, mvnt, toConsume, false)
    }

    performTest("aa", 'a' || "aa")
    performTest("abababc", 'a' || "ab".zeroOrMore || ("ab".atLeastOnce ~ 'c'))
  }

  private implicit class SourceWithMarkerOps(underlying: SourceWithMarker) {
    def currentStr = underlying.current.toString
  }
}
