package scala.tools.refactoring
package tests.implementations.extraction

import implementations.extraction.ExtractCode
import tests.util.TestHelper
import tests.util.TestRefactoring

class ExtractCodeTest extends TestHelper with TestRefactoring {
  def extract(extractionIdx: Int)(pro: FileSet) = {
    val testRefactoring = new TestRefactoringImpl(pro) {
      val refactoring = new ExtractCode with TestProjectIndex
      val e = preparationResult.right.get.extractions(extractionIdx)
    }
    testRefactoring.performRefactoring(testRefactoring.e)
  }

  @Test
  def extractCodeWithoutUnknownDependencies() = new FileSet {
    """
      object Demo {
        val a = 1
        val b = /*(*/a * a/*)*/
      }
    """ becomes
      """
      object Demo {
        val a = 1
        val b = extracted
        val extracted = a * a
      }
    """
  }.performRefactoring(extract(2)).assertEqualTree

  @Test
  def extractCodeWithUnknownDependencies() = new FileSet {
    """
      object Demo {
        val a = 1
        val b = {
          val c = 2
          /*(*/a * c/*)*/
        }
      }
    """ becomes
      """
      object Demo {
        val a = 1

        val b = {
          val c = 2
          extracted(c)
        }

        def extracted(c: Int): Int = {
          a * c
        }
      }
    """
  }.performRefactoring(extract(2)).assertEqualTree

  @Test
  def extractMultipleExpressions() = new FileSet {
    """
      object Demo {
        val a = 1
        val b = {
          /*(*/val c = 2
          val d = a
          d * c/*)*/
        }
      }
    """ becomes
      """
      object Demo {
        val a = 1
        val b = {
          val extracted = {
            val c = 2
            val d = a
            d * c
          }
          extracted
        }
      }
    """
  }.performRefactoring(extract(0)).assertEqualTree

  @Test
  def extractUnitExpressionToDef() = new FileSet {
    """
      object Demo {
        /*(*/println("hello world")/*)*/
      }
    """ becomes
      """
      object Demo {
        extracted

        def extracted(): Unit = {
          println("hello world")
        }
      }
    """
  }.performRefactoring(extract(1)).assertEqualTree

  @Test
  def extractCodeInCase() = new FileSet {
    """
      object Demo {
        val p = (1, 1) match {
          case (x: Int, y: Int) => /*(*/x * y/*)*/
        }
      }
    """ becomes
      """
      object Demo {
        val p = (1, 1) match {
          case (x: Int, y: Int) =>
            val extracted = x * y
            extracted
        }
      }
    """
  }.performRefactoring(extract(0)).assertEqualTree

  @Test
  def extractCodeWithPotentialSideEffects() = new FileSet {
    """
      object Demo {
        val a = {
          /*(*/println("calculate answer...")
          6 * 7/*)*/
        }
      }
    """ becomes
      """
      object Demo {
        val a = extracted

        def extracted(): Int = {
          println("calculate answer...")
          6 * 7
        }
      }
    """
  }.performRefactoring(extract(3)).assertEqualTree

  @Test
  def extractCodeWithPotentialSideEffectsOnVar() = new FileSet {
    """
      object Demo {
        var c = 1
        val a = {
          /*(*/c += 1
          6 * 7/*)*/
        }
      }
    """ becomes
      """
      object Demo {
        var c = 1

        val a = extracted

        def extracted(): Int = {
          c += 1
          6 * 7
        }
      }
    """
  }.performRefactoring(extract(3)).assertEqualTree

  @Test
  def extractForEnumerator() = new FileSet {
    """
      object Demo {
        for{
          i <- /*(*/1 to 100/*)*/
        } println(i)
      }
    """ becomes
      """
      object Demo {
        for{
          i <- extracted
        } println(i)

        val extracted = 1 to 100
      }
    """
  }.performRefactoring(extract(0)).assertEqualTree

  @Test
  def extractFromForBody() = new FileSet {
    """
      object Demo {
        for{
          i <- 1 to 100
        } /*(*/println(i)/*)*/
      }
    """ becomes
      """
      object Demo {
        for{
          i <- 1 to 100
        } extracted(i)

        def extracted(i: Int): Unit = {
          println(i)
        }
      }
    """
  }.performRefactoring(extract(0)).assertEqualTree

  @Test
  def extractCase() = {
    new FileSet {
      """
      object Demo {
        1 match {
          /*(*/case _ => println(1)/*)*/
        }
      }
    """ becomes """
      object Demo {
        extracted

        def extracted(): Unit = {
          1 match {
            /*(*/case _ => println(1)/*)*/
          }
        }
      }
    """
    }.performRefactoring(extract(1)).assertEqualTree
  }

  @Test
  def extractConstructorCall() = new FileSet {
    """
      object Demo {
        /*(*/List(1, 2, 3)/*)*/
      }
    """ becomes
      """
      object Demo {
        extracted

        val extracted = List(1, 2, 3)
      }
    """
  }.performRefactoring(extract(0)).assertEqualTree

  @Test
  def extractExtractor() = new FileSet {
    """
      object Demo {
        1 match {
            case /*(*/i: Int/*)*/ => println(i)
        }
      }
    """ becomes
      """
      object Demo {
        1 match {
            case Extracted(i) => println(i)
        }

        object Extracted {
          def unapply(x: Int) = x match {
            case i => Some(i)
            case _ => None
          }
        }
      }
    """
  }.performRefactoring(extract(0)).assertEqualTree

  @Test
  def extractWithNothingSelected() = new FileSet {
    """
      object Demo {
        def fn = {
          val a = 1/*<-*/
        }
      }
    """ becomes
      """
      object Demo {
        def fn = {
          val extracted = 1
          val a = extracted
        }
      }
    """
  }.performRefactoring(extract(2)).assertEqualTree

  def avoidNameCollisions() = new FileSet {
    """
      object Demo {
        val extracted = 1
        val extracted1 = /*(*/2/*)*/
      }
    """ becomes
      """
      object Demo {
        val extracted = 1
        val extracted1 = {
          val extracted2 = 2
          extracted2
        }
      }
    """
  }.performRefactoring(extract(1)).assertEqualTree
}
