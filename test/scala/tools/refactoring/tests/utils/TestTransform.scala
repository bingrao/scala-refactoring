package scala.tools.refactoring.tests.utils

import scala.tools.refactoring.Compiler
import scala.tools.refactoring.transformation.Transform
import scala.tools.refactoring.UnknownPosition
import scala.tools.nsc.util.Position
import scala.tools.nsc.util.RangePosition
import scala.tools.nsc.ast.parser.Tokens
import scala.tools.nsc.ast.TreeDSL
import scala.tools.nsc.symtab.Flags

trait TestTransform extends Transform with TreeDSL {
  
  self: scala.tools.refactoring.Compiler =>
  import CODE._
  import global._
  
  def insertNewMethod = new Transformer {
    override def transform(tree: Tree): Tree = {
      super.transform(tree) match {
        
        case tpl @ Template(parents, self, body) => 
          
            val typ: TypeTree = body(1) match {
              case tree: DefDef => tree.tpt.asInstanceOf[TypeTree]
            }
            
            val v = ValDef(NoMods, newTermName("sample"), TypeTree(typ.tpe), EmptyTree)

            val block = Block( Literal(555) :: v :: Nil, EmptyTree)
                      
            val d = cleanTree {
              DefDef(Modifiers(Flags.METHOD), newTermName("method"), Nil, Nil, TypeTree(typ.tpe), block)
            }
            
            new Template(parents, self, d :: body).copyAttrs(tree)
          
        case x => x
      }
    }
  }
  
  def copyLastMethod = new Transformer {
    override def transform(tree: Tree): Tree = {
      super.transform(tree) match {
        
        case tpl @ Template(parents, self, body) => 
          
            new Template(parents, self, body.last :: body).copyAttrs(tree)
          
        case x => x
      }
    }
  }
  
  def newMethodFromExistingBody = new Transformer {
    override def transform(tree: Tree): Tree = {
      super.transform(tree) match {
        
        case tpl @ Template(parents, self, body) => 
          
          val typ: TypeTree = body(1) match {
            case tree: DefDef => tree.tpt.asInstanceOf[TypeTree]
          }
          
          val v = ValDef(NoMods, "arg", TypeTree(typ.tpe), EmptyTree)
          
          val rhs = body.last match {
            case tree: ValOrDefDef => tree.rhs
          }

          val d = cleanTree {
            DefDef(Modifiers(Flags.METHOD), "newMethod", Nil, (v :: Nil) :: Nil, TypeTree(typ.tpe), rhs)
          }
          
          new Template(parents, self, d :: body).copyAttrs(tree)
        case x => x
      }
    }
  }  
  
  def bodyInBody = new Transformer {
    override def transform(tree: Tree): Tree = {
      super.transform(tree) match {
        
        case defdef @ DefDef(mods, name, tparams, vparamss, tpt, rhs: Block) if defdef.pos.isRange =>
        
          val newDef = DefDef(Modifiers(Flags.METHOD), "innerMethod", Nil, Nil, TypeTree(rhs.expr.tpe), rhs) 
        
          val newRhs = cleanTree {
            Block(
                newDef
                :: Nil
                , Apply(Select(This(""), "innerMethod"), Nil))
          }
          
          new DefDef(mods, name, tparams, vparamss, tpt, newRhs).copyAttrs(tree)
        

        case x => x
      }
    }
  }  
  
  def newMethod = new Transformer {
    override def transform(tree: Tree): Tree = {
      super.transform(tree) match {
        
        case defdef @ DefDef(mods, name, tparams, vparamss, tpt, rhs: Block) if defdef.pos.isRange =>
        
          val v = ValDef(NoMods, "arg1", TypeTree(definitions.IntClass.tpe), EmptyTree)
          val v2 = VAL(defdef.symbol.newValue(UnknownPosition, "v2") setInfo definitions.StringClass.tpe) === NULL
        
          val newDef = DefDef(Modifiers(Flags.METHOD), "innerMethod", Nil, (v :: v2 :: Nil) :: Nil, TypeTree(rhs.expr.tpe), rhs) 
        
          val newRhs = cleanTree {
            Block(
                newDef :: rhs.stats ::: rhs.expr :: Nil
                , Apply(Select(This(""), "innerMethod"), Nil))
          }
          
          new DefDef(mods, name, tparams, vparamss, tpt, newRhs).copyAttrs(tree)
        

        case x => x
      }
    }
  }
}
