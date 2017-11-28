/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.refactoring
package tests.implementations

import implementations.ExplicitGettersSetters
import tests.util.TestHelper
import tests.util.TestRefactoring


class ExplicitGettersSettersTest extends TestHelper with TestRefactoring {
  outer =>

  def explicitGettersSetters(pro: FileSet) = new TestRefactoringImpl(pro) {
    val refactoring = new ExplicitGettersSetters  {
      val global = outer.global
    }
    val changes = performRefactoring()
  }.changes

  @Test
  @ScalaVersion(doesNotMatch = "2.12")
  def oneVarFromMany() = new FileSet {
    """
      package oneFromMany
      class Demo(val a: String,  /*(*/var i: Int/*)*/  ) {
        def doNothing = ()
      }
    """ becomes
    """
      package oneFromMany
      class Demo(val a: String,  /*(*/private var _i: Int/*)*/  ) {
        def i = {
          _i
        }

        def i_=(i: Int) = {
          _i = i
        }
        def doNothing = ()
      }
    """
  } applyRefactoring(explicitGettersSetters)

  @Test
  @ScalaVersion(doesNotMatch = "2.12")
  def oneValFromMany() = new FileSet {
    """
      package oneFromMany
      class Demo(val a: String,  /*(*/val i: Int/*)*/  ) {
        def doNothing = ()
      }
    """ becomes
    """
      package oneFromMany
      class Demo(val a: String,  /*(*/_i: Int/*)*/  ) {
        def i = {
          _i
        }

        def doNothing = ()
      }
    """
  } applyRefactoring(explicitGettersSetters)

  @Test
  @ScalaVersion(doesNotMatch = "2.12")
  def singleVal() = new FileSet {
    """
      package oneFromMany
      class Demo(  /*(*/val i: Int/*)*/  )
    """ becomes
    """
      package oneFromMany
      class Demo(  /*(*/_i: Int/*)*/  ) {
        def i = {
          _i
        }
      }
    """
  } applyRefactoring(explicitGettersSetters)

  @Test
  @ScalaVersion(doesNotMatch = "2.12")
  def singleValWithEmptyBody() = new FileSet {
    """
      package oneFromMany
      class Demo(  /*(*/val i: Int/*)*/  ) {

      }
    """ becomes
    """
      package oneFromMany
      class Demo(  /*(*/_i: Int/*)*/  ) {
        def i = {
          _i
        }
      }
    """
  } applyRefactoring(explicitGettersSetters)
}
