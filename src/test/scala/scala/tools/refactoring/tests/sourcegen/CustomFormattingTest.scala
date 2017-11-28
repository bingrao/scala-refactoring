/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.refactoring
package tests.sourcegen

import tests.util.TestHelper
import org.junit.Assert._
import sourcegen.SourceGenerator
import scala.tools.refactoring.implementations.OrganizeImports
import scala.tools.refactoring.tests.util.TestRefactoring


class CustomFormattingTest extends TestHelper with TestRefactoring with SourceGenerator {

  @volatile
  private var surroundingImport = ""

  override def spacingAroundMultipleImports = surroundingImport

  abstract class OrganizeImportsRefatoring(pro: FileSet) extends TestRefactoringImpl(pro) {
    val refactoring = new OrganizeImports {
      val global = CustomFormattingTest.this.global
      override def spacingAroundMultipleImports = surroundingImport
    }
    type RefactoringParameters = refactoring.RefactoringParameters
    val params: RefactoringParameters
    def mkChanges = performRefactoring(params)
  }

  def organize(pro: FileSet) = new OrganizeImportsRefatoring(pro) {
    val config = OrganizeImports.OrganizeImportsConfig(
        importsStrategy = Some(OrganizeImports.ImportsStrategy.CollapseImports)
    )
    val params = new RefactoringParameters(config = Some(config))
  }.mkChanges


  @Test
  def testSingleSpace(): Unit = {

    val ast = treeFrom("""
    package test
    import scala.collection.{MapLike, MapProxy}
    """)

    surroundingImport = " "

    assertEquals("""
    package test
    import scala.collection.{ MapLike, MapProxy }
    """, createText(ast, Some(ast.pos.source)))
  }

  @Test
  def collapse() = {
    surroundingImport = " "

    new FileSet {
      """
        import java.lang.String
        import java.lang.Object

        object Main {val s: String = ""; var o: Object = null}
      """ becomes
      """
        import java.lang.{ Object, String }

        object Main {val s: String = ""; var o: Object = null}
      """
    } applyRefactoring organize
  }
}

