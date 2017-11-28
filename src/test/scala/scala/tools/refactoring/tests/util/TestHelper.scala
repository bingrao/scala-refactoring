/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.refactoring
package tests.util

import scala.Option.option2Iterable
import scala.collection.mutable.ListBuffer
import scala.tools.nsc.util.FailedInterrupt
import scala.tools.refactoring.Refactoring
import scala.tools.refactoring.common.Change
import scala.tools.refactoring.common.NewFileChange
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.util.CompilerProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import scala.tools.refactoring.common.InteractiveScalaCompiler
import language.{ postfixOps, reflectiveCalls }
import scala.tools.refactoring.common.NewFileChange
import scala.tools.refactoring.common.RenameSourceFileChange
import scala.tools.refactoring.implementations.Rename
import scala.tools.refactoring.common.TracingImpl
import scala.tools.refactoring.util.UniqueNames
import scala.util.Try
import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.common.MoveToDirChange

object TestHelper {
  case class PrepResultWithChanges(prepResult: Option[Either[MultiStageRefactoring#PreparationError, Rename#PreparationResult]], changes: List[Change])

  private val IndentedLine = """(\s*)[^\s].*""".r
}

trait TestHelper extends TestRules with Refactoring with CompilerProvider with common.InteractiveScalaCompiler with TracingImpl {
  import TestHelper._

  @Before
  def cleanup() = resetPresentationCompiler()

  type Test = org.junit.Test
  type Ignore = org.junit.Ignore
  type AbstractFile = tools.nsc.io.AbstractFile
  type GlobalIndexes = analysis.GlobalIndexes
  type ScalaVersion = tests.util.ScalaVersion

  private case class Source(code: String, path: String) {
    def toPair = (code, path)
  }

  private object Source {
    def apply(codeWithFilename: (String, String)) = new Source(codeWithFilename._1, codeWithFilename._2)
  }

  /**
   * If this method returns true, all `FileSet` based test cases are nested in unique base packages by default
   *
   * Note that this is useful since test cases are usually executed with the same presentation compiler for
   * performance reasons. By nesting them in unique packages, we minimize the chance that they interfere
   * with each other.
   */
  protected def nestTestsInUniqueBasePackageByDefault = false

  private def defaultFileSetBasePackage = {
    if (nestTestsInUniqueBasePackageByDefault) Some(UniqueNames.scalaPackage())
    else None
  }

  protected def parseScalaAndVerify(src: String): global.Tree = {
    val tree = treeFrom(src)

    val errorFound = global.ask {() =>
      tree.find(t => t.isErroneous || t.isErrorTyped).nonEmpty
    }

    assertFalse("Error compiling test source", errorFound)
    tree
  }

  /**
   * A project to test multiple compilation units. Add all
   * sources using "add" before using any of the lazy vals.
   */
  abstract class FileSet(baseName: String = UniqueNames.basename(), val expectCompilingCode: Boolean = true, val basePackage: Option[String] = defaultFileSetBasePackage) {
    private val srcs = ListBuffer[(Source, Source)]()
    private val javaSrcs = ListBuffer[Source]()

    object TaggedAsGlobalRename
    object TaggedAsLocalRename

    /**
     * To preload java source code in tests.
     *
     * To be used like
     * {{{
     *   //...
     *   """
     *   class SomeJavaClass {
     *   }
     *   """ -> PreloadAsJava("SomeJavaClass")
     *   //...
     * }}}
     */
    case class PreloadAsJava(className: String)

    var expectGlobalRename: Option[Boolean] = None

    private def eventuallyNestInBasePgk(src: Source): Source = {
      basePackage.map { pkg =>
        val initialIndent = src.code.lines.collectFirst {
          case IndentedLine(indent) => indent
        }.getOrElse("")

        src.copy(code = s"${initialIndent}package $pkg;\n\n${src.code}")
      }.getOrElse(src)
    }

    private def eventuallyNestInBasePgk(src1: Source, src2: Source): (Source, Source) = {
      (eventuallyNestInBasePgk(src1), eventuallyNestInBasePgk(src2))
    }

    implicit class WrapSource(src: String) {
      def becomes(expected: String): Unit = {
        val filename = nextFilename()
        srcs += eventuallyNestInBasePgk(Source(src, filename), Source(stripWhitespacePreservers(expected), filename))
      }

      def isNotModified(): Unit = {
        becomes(src)
      }

      def ->(tag: TaggedAsGlobalRename.type): String = {
        expectGlobalRename = Some(true)
        src
      }

      def ->(tag: TaggedAsLocalRename.type): String = {
        expectGlobalRename = Some(false)
        src
      }

      def ->(preload: PreloadAsJava): Unit = {
        javaSrcs += eventuallyNestInBasePgk(Source(src, preload.className + ".java"))
      }

      def ->(filename: String): (String, String) = {
        (src, filename)
      }
    }

    implicit class WrapSourceWithFilename(srcWithName: (String, String)) {
      def becomes(newSrcWithName: (String, String)): Unit = {
        srcs += eventuallyNestInBasePgk(Source(srcWithName), Source(stripWhitespacePreservers(newSrcWithName._1), newSrcWithName._2))
      }
    }

    private def nextFilename() = {
      baseName + "_" + srcs.size + ".scala"
    }

    lazy val sources = srcs.unzip._1.map(_.toPair).toList

    lazy val javaSources = javaSrcs.map(_.toPair).toList

    val NewFile = ""

    def applyRefactoring(createChanges: FileSet => List[Change]): Unit = {
      performRefactoring(createChanges).assertEqualSource
    }

    def prepareAndApplyRefactoring(result: FileSet => PrepResultWithChanges): Unit = {
      prepareAndPerformRefactoring(result).assertOk()
    }

    def apply(f: FileSet => List[String]) = assert(f(this))

    private def assert(res: List[String], sourceRenames: List[RenameSourceFileChange] = Nil, fileMoves: List[MoveToDirChange] = Nil) = {
      assertEquals(srcs.length, res.length)
      val expected = srcs.unzip._2.toList.map(_.code)
      expected zip res foreach (p => assertEquals(p._1, p._2))

      fileRenameOps.foreach { case (oldPath, newPath) =>
        val (oldPathComponents, newPathComponents) = (oldPath.split("/"), newPath.split("/"))

        val differentComponents = oldPathComponents.zip(newPathComponents).filter { case (o, n) => o != n }
        val nDifferentComponents = differentComponents.size
        if (nDifferentComponents > 1 || oldPathComponents.size != newPathComponents.size) {
          throw new AssertionError("Generic moves are not supported")
        }

        val (oldFileName, newFileName) = (oldPathComponents.last, newPathComponents.last)

        if (oldFileName != newFileName) {
          assertTrue(s"Missing file rename operation $oldFileName -> $newFileName", sourceRenames.exists { r =>
            r.sourceFile.name == oldFileName && r.to == newFileName
          })
        } else {
          differentComponents.headOption match {
            case Some((oldDir, newDir)) =>
              assertTrue(s"Missing dir rename operation $oldDir -> $newDir in $fileMoves", fileMoves.exists { m =>
                val oldPath = m.sourceFile.path
                val dest = m.to

                oldPath.contains(s"$oldDir/") &&
                  oldPath.endsWith(oldFileName) &&
                  dest.contains(newDir)
              })

            case None =>
              assertTrue(s"Illegal move operation in $fileMoves", !fileMoves.exists { m =>
                m.sourceFile.path.endsWith(oldPath)
              })
          }
        }
      }

      assertNoDuplicates(sourceRenames.map(_.to), d => s"Destination '$d' appearing multiple times")
      assertNoDuplicates(sourceRenames.map(_.sourceFile.canonicalPath), s => s"Source '$s' appearing multiple times")
    }

    private def assertNoDuplicates[T](values: Seq[T], mkErrMsg: Any => String): Unit = {
      values.groupBy(identity).foreach { case (v, vs) =>
        assertTrue(mkErrMsg(v), vs.size < 2)
      }
    }

    private def fileRenameOps = {
      srcs.collect { case (oldSource, newSource) if oldSource.path != newSource.path =>
        (oldSource.path, newSource.path)
      }
    }

    def prepareAndPerformRefactoring(createChanges: FileSet => PrepResultWithChanges) = {
      val PrepResultWithChanges(prepResult, changes) = try {
        global.ask { () =>
          createChanges(this)
        }
      } catch {
        case e: FailedInterrupt =>
          throw e.getCause
        case e: InterruptedException =>
          throw e.getCause
      }

      val refactoredCode = sources flatMap {
        case (NewFile, name) =>
          changes collect {
            case nfc: NewFileChange => nfc.text
          }

        case (src, name) =>
          val textFileChanges = changes collect {
            case tfc: TextChange if tfc.sourceFile.file.name == name => tfc
          }
          Change.applyChanges(textFileChanges, src) :: Nil

      } filterNot (_.isEmpty)

      val sourceRenames = changes.collect { case d: RenameSourceFileChange => d }

      val fileMoves = changes.collect { case d: MoveToDirChange => d }

      new {
        def withResultTree(fn: global.Tree => Unit) = fn(treeFrom(refactoredCode.mkString("\n")))
        def withResultSource(fn: String => Unit) = fn(refactoredCode.mkString("\n"))
        def assertEqualSource() = assert(refactoredCode, sourceRenames, fileMoves)

        def assertOk() = {
          expectGlobalRename.foreach { expectGlobalRename =>
            assertEquals(s"Wrong value for globalRename - ", expectGlobalRename, !prepResult.get.right.get.hasLocalScope)
          }
          assertEqualSource()
        }

        def assertEqualTree() = withResultTree { actualTree =>
          val expectedTree = treeFrom(srcs.head._2.code)
          val (expected, actual) = global.ask { () =>
            (expectedTree.toString(), actualTree.toString())
          }
          assertEquals(expected, actual)
        }
      }
    }

    /**
     * Same as applyRefactoring but does not make the assertion on the
     * refactoring result.
     */
    def performRefactoring(createChanges: FileSet => List[Change]) = {
      prepareAndPerformRefactoring(createChanges.andThen(PrepResultWithChanges(None, _)))
    }

  }

  def selection(refactoring: Selections with InteractiveScalaCompiler, project: FileSet) = {
    def containsError(t: refactoring.global.Tree): Boolean = {
      def symbolContainsError = {
        val sym = t.symbol
        sym != null && {
          if (sym.isErroneous || sym.isError) {
            true
          } else {
            sym.annotations.exists { annotation =>
              containsError(annotation.original)
            }
          }
        }
      }

      t.isErroneous || symbolContainsError
    }

    val files = project.sources map { case (code, filename) => addToCompiler(filename, code)}
    val trees: List[refactoring.global.Tree] = files map (refactoring.global.unitOfFile(_).body)

    if (project.expectCompilingCode) {
      trees.foreach { tree =>
        tree.find(containsError).foreach { erroneousTree =>
          val src = new String(tree.pos.source.content)
          val sep = "------------------------------------"
          throw new AssertionError(s"Expected compiling code but got:\n$sep\n$src\n$sep\nErroneous tree: $erroneousTree")
        }
      }
    }

    (project.sources zip trees flatMap {
      case (src, tree) =>
        findMarkedNodes(refactoring)(src._1, tree)
    } headOption) getOrElse {
      refactoring.FileSelection(trees.head.pos.source.file, trees.head, 0, 0)
    }
  }

  def findMarkedNodes(r: Selections with InteractiveScalaCompiler)(src: String, tree: r.global.Tree): Option[r.Selection] = {
    Try(TextSelections.extractOne(src)).toOption.map { textSelection =>
      r.FileSelection(tree.pos.source.file, tree, textSelection.from, textSelection.to)
    }
  }

  def cleanTree(t: global.Tree) = {
    global.ask { () =>
      val removeAuxiliaryTrees = ↓(transform {

        case t: global.Tree if (t.pos == global.NoPosition || t.pos.isRange) => t

        case t: global.ValDef => global.emptyValDef

        // We want to exclude "extends AnyRef" in the pretty printer tests
        case t: global.Select if t.name.isTypeName && t.name.toString != "AnyRef" => t

        case t => global.EmptyTree
      })

      (removeAuxiliaryTrees &> topdown(setNoPosition))(t).get
    }
  }

  def stripWhitespacePreservers(s: String) = s.replaceAll("▒", "")

  def findMarkedNodes(src: String, tree: global.Tree) = {
    val textSelection = TextSelections.extractOne(src)
    FileSelection(tree.pos.source.file, tree, textSelection.from, textSelection.to)
  }

  def toSelection(src: String) = global.ask{ () =>
    val tree = treeFrom(src)
    findMarkedNodes(src, tree)
  }
}
