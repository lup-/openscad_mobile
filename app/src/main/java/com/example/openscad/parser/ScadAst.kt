package com.example.openscad.parser

sealed class Expr {
    data class Number(val value: Float) : Expr()
    data class StringLit(val value: String) : Expr()
    data class BoolLit(val value: Boolean) : Expr()
    data class Variable(val name: String) : Expr()
    data class ArrayLit(val elements: List<Expr>) : Expr()
    data class Binary(val left: Expr, val op: String, val right: Expr) : Expr()
    data class Unary(val op: String, val operand: Expr) : Expr()
    data class Call(val name: String, val args: List<NamedArg>) : Expr()
    data class RangeExpr(val start: Expr, val step: Expr?, val end: Expr) : Expr()
}

data class NamedArg(
    val name: String?, // null if positional
    val expr: Expr
)

sealed class AstNode {
    data class VarAssign(val name: String, val expr: Expr) : AstNode()
    data class ModuleDef(val name: String, val params: List<Pair<String, Expr?>>, val children: List<AstNode>) : AstNode()
    data class ModuleCall(val name: String, val args: List<NamedArg>, val children: List<AstNode> = emptyList()) : AstNode()
    data class ForLoop(val varName: String, val rangeExpr: Expr, val children: List<AstNode>) : AstNode()
    data class IfStmt(val condition: Expr, val thenBranch: List<AstNode>, val elseBranch: List<AstNode> = emptyList()) : AstNode()
    data class Block(val nodes: List<AstNode>) : AstNode()
}
