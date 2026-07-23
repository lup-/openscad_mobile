package com.example.openscad.parser

import java.util.Locale

class ScadParser(private val input: String) {

    private enum class TokenType {
        NUMBER, STRING, IDENTIFIER,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        LPAREN, RPAREN, LBRACK, RBRACK, LBRACE, RBRACE,
        COMMA, SEMICOLON, COLON, EQUAL,
        EQ_EQ, NOT_EQ, LESS, GREATER, LESS_EQ, GREATER_EQ,
        AND, OR, NOT,
        EOF
    }

    private data class Token(
        val type: TokenType,
        val text: String,
        val line: Int
    )

    private val tokens = mutableListOf<Token>()
    private var pos = 0
    val parseErrors = mutableListOf<String>()

    init {
        tokenize()
    }

    private fun tokenize() {
        var line = 1
        var i = 0
        val len = input.length

        while (i < len) {
            val c = input[i]

            if (c == '\n') {
                line++
                i++
                continue
            }
            if (c.isWhitespace()) {
                i++
                continue
            }

            // Comments
            if (c == '/' && i + 1 < len && input[i + 1] == '/') {
                i += 2
                while (i < len && input[i] != '\n') i++
                continue
            }
            if (c == '/' && i + 1 < len && input[i + 1] == '*') {
                i += 2
                while (i + 1 < len && !(input[i] == '*' && input[i + 1] == '/')) {
                    if (input[i] == '\n') line++
                    i++
                }
                i += 2
                continue
            }

            // Numbers
            if (c.isDigit() || (c == '.' && i + 1 < len && input[i + 1].isDigit())) {
                val start = i
                while (i < len && (input[i].isDigit() || input[i] == '.')) i++
                tokens.add(Token(TokenType.NUMBER, input.substring(start, i), line))
                continue
            }

            // Strings
            if (c == '"') {
                i++
                val start = i
                while (i < len && input[i] != '"') {
                    if (input[i] == '\n') line++
                    i++
                }
                val str = input.substring(start, i)
                if (i < len) i++ // skip quote
                tokens.add(Token(TokenType.STRING, str, line))
                continue
            }

            // Identifiers / Keywords
            if (c.isLetter() || c == '_' || c == '$') {
                val start = i
                while (i < len && (input[i].isLetterOrDigit() || input[i] == '_' || input[i] == '$')) i++
                tokens.add(Token(TokenType.IDENTIFIER, input.substring(start, i), line))
                continue
            }

            // Two-char operators
            if (i + 1 < len) {
                val two = input.substring(i, i + 2)
                val type = when (two) {
                    "==" -> TokenType.EQ_EQ
                    "!=" -> TokenType.NOT_EQ
                    "<=" -> TokenType.LESS_EQ
                    ">=" -> TokenType.GREATER_EQ
                    "&&" -> TokenType.AND
                    "||" -> TokenType.OR
                    else -> null
                }
                if (type != null) {
                    tokens.add(Token(type, two, line))
                    i += 2
                    continue
                }
            }

            // Single-char operators
            val type = when (c) {
                '+' -> TokenType.PLUS
                '-' -> TokenType.MINUS
                '*' -> TokenType.STAR
                '/' -> TokenType.SLASH
                '%' -> TokenType.PERCENT
                '(' -> TokenType.LPAREN
                ')' -> TokenType.RPAREN
                '[' -> TokenType.LBRACK
                ']' -> TokenType.RBRACK
                '{' -> TokenType.LBRACE
                '}' -> TokenType.RBRACE
                ',' -> TokenType.COMMA
                ';' -> TokenType.SEMICOLON
                ':' -> TokenType.COLON
                '=' -> TokenType.EQUAL
                '<' -> TokenType.LESS
                '>' -> TokenType.GREATER
                '!' -> TokenType.NOT
                else -> null
            }

            if (type != null) {
                tokens.add(Token(type, c.toString(), line))
                i++
            } else {
                parseErrors.add("Line $line: Unexpected character '$c'")
                i++
            }
        }
        tokens.add(Token(TokenType.EOF, "", line))
    }

    private fun peek(): Token = tokens[pos]
    private fun advance(): Token = tokens[pos++]
    private fun check(type: TokenType): Boolean = peek().type == type

    private fun match(type: TokenType): Boolean {
        if (check(type)) {
            advance()
            return true
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        parseErrors.add("Line ${peek().line}: $message (found '${peek().text}')")
        return peek()
    }

    fun parseProgram(): List<AstNode> {
        val nodes = mutableListOf<AstNode>()
        while (!check(TokenType.EOF)) {
            try {
                val node = parseStatement()
                if (node != null) nodes.add(node)
            } catch (e: Exception) {
                parseErrors.add("Line ${peek().line}: ${e.message}")
                advance()
            }
        }
        return nodes
    }

    private fun parseStatement(): AstNode? {
        if (check(TokenType.EOF)) return null

        val token = peek()
        if (token.type == TokenType.IDENTIFIER) {
            when (token.text) {
                "module" -> return parseModuleDef()
                "for" -> return parseForLoop()
                "if" -> return parseIfStmt()
            }

            // Variable assignment vs module call
            if (pos + 1 < tokens.size && tokens[pos + 1].type == TokenType.EQUAL) {
                return parseVarAssign()
            } else {
                return parseModuleCall()
            }
        }

        if (match(TokenType.LBRACE)) {
            val children = mutableListOf<AstNode>()
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                val stmt = parseStatement()
                if (stmt != null) children.add(stmt)
            }
            consume(TokenType.RBRACE, "Expected '}'")
            return AstNode.Block(children)
        }

        parseErrors.add("Line ${token.line}: Unexpected statement token '${token.text}'")
        advance()
        return null
    }

    private fun parseVarAssign(): AstNode {
        val id = consume(TokenType.IDENTIFIER, "Expected variable name").text
        consume(TokenType.EQUAL, "Expected '='")
        val expr = parseExpression()
        match(TokenType.SEMICOLON)
        return AstNode.VarAssign(id, expr)
    }

    private fun parseModuleDef(): AstNode {
        consume(TokenType.IDENTIFIER, "Expected 'module'")
        val name = consume(TokenType.IDENTIFIER, "Expected module name").text
        consume(TokenType.LPAREN, "Expected '(' after module name")

        val params = mutableListOf<Pair<String, Expr?>>()
        if (!check(TokenType.RPAREN)) {
            do {
                val paramName = consume(TokenType.IDENTIFIER, "Expected parameter name").text
                var defaultExpr: Expr? = null
                if (match(TokenType.EQUAL)) {
                    defaultExpr = parseExpression()
                }
                params.add(Pair(paramName, defaultExpr))
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Expected ')'")

        val children = mutableListOf<AstNode>()
        if (check(TokenType.LBRACE)) {
            val block = parseStatement() as? AstNode.Block
            if (block != null) children.addAll(block.nodes)
        } else {
            val stmt = parseStatement()
            if (stmt != null) children.add(stmt)
        }

        return AstNode.ModuleDef(name, params, children)
    }

    private fun parseForLoop(): AstNode {
        consume(TokenType.IDENTIFIER, "Expected 'for'")
        consume(TokenType.LPAREN, "Expected '('")
        val varName = consume(TokenType.IDENTIFIER, "Expected loop variable").text
        consume(TokenType.EQUAL, "Expected '='")
        val rangeExpr = parseExpression()
        consume(TokenType.RPAREN, "Expected ')'")

        val children = mutableListOf<AstNode>()
        if (check(TokenType.LBRACE)) {
            val block = parseStatement() as? AstNode.Block
            if (block != null) children.addAll(block.nodes)
        } else {
            val stmt = parseStatement()
            if (stmt != null) children.add(stmt)
        }

        return AstNode.ForLoop(varName, rangeExpr, children)
    }

    private fun parseIfStmt(): AstNode {
        consume(TokenType.IDENTIFIER, "Expected 'if'")
        consume(TokenType.LPAREN, "Expected '('")
        val cond = parseExpression()
        consume(TokenType.RPAREN, "Expected ')'")

        val thenList = mutableListOf<AstNode>()
        if (check(TokenType.LBRACE)) {
            val block = parseStatement() as? AstNode.Block
            if (block != null) thenList.addAll(block.nodes)
        } else {
            val stmt = parseStatement()
            if (stmt != null) thenList.add(stmt)
        }

        val elseList = mutableListOf<AstNode>()
        if (peek().text == "else") {
            advance()
            if (check(TokenType.LBRACE)) {
                val block = parseStatement() as? AstNode.Block
                if (block != null) elseList.addAll(block.nodes)
            } else {
                val stmt = parseStatement()
                if (stmt != null) elseList.add(stmt)
            }
        }

        return AstNode.IfStmt(cond, thenList, elseList)
    }

    private fun parseModuleCall(): AstNode {
        val name = consume(TokenType.IDENTIFIER, "Expected module name").text
        val args = mutableListOf<NamedArg>()

        if (match(TokenType.LPAREN)) {
            if (!check(TokenType.RPAREN)) {
                do {
                    var argName: String? = null
                    if (pos + 1 < tokens.size && tokens[pos].type == TokenType.IDENTIFIER && tokens[pos + 1].type == TokenType.EQUAL) {
                        argName = advance().text
                        advance() // '='
                    }
                    val expr = parseExpression()
                    args.add(NamedArg(argName, expr))
                } while (match(TokenType.COMMA))
            }
            consume(TokenType.RPAREN, "Expected ')'")
        }

        val children = mutableListOf<AstNode>()
        if (check(TokenType.LBRACE)) {
            val block = parseStatement() as? AstNode.Block
            if (block != null) children.addAll(block.nodes)
        } else if (!check(TokenType.SEMICOLON) && !check(TokenType.EOF)) {
            val child = parseStatement()
            if (child != null) children.add(child)
        } else {
            match(TokenType.SEMICOLON)
        }

        return AstNode.ModuleCall(name, args, children)
    }

    private fun parseExpression(): Expr {
        return parseLogicalOr()
    }

    private fun parseLogicalOr(): Expr {
        var expr = parseLogicalAnd()
        while (match(TokenType.OR)) {
            val right = parseLogicalAnd()
            expr = Expr.Binary(expr, "||", right)
        }
        return expr
    }

    private fun parseLogicalAnd(): Expr {
        var expr = parseEquality()
        while (match(TokenType.AND)) {
            val right = parseEquality()
            expr = Expr.Binary(expr, "&&", right)
        }
        return expr
    }

    private fun parseEquality(): Expr {
        var expr = parseComparison()
        while (check(TokenType.EQ_EQ) || check(TokenType.NOT_EQ)) {
            val op = advance().text
            val right = parseComparison()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun parseComparison(): Expr {
        var expr = parseAdditive()
        while (check(TokenType.LESS) || check(TokenType.GREATER) || check(TokenType.LESS_EQ) || check(TokenType.GREATER_EQ)) {
            val op = advance().text
            val right = parseAdditive()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun parseAdditive(): Expr {
        var expr = parseMultiplicative()
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            val op = advance().text
            val right = parseMultiplicative()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun parseMultiplicative(): Expr {
        var expr = parseUnary()
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            val op = advance().text
            val right = parseUnary()
            expr = Expr.Binary(expr, op, right)
        }
        return expr
    }

    private fun parseUnary(): Expr {
        if (check(TokenType.MINUS) || check(TokenType.NOT)) {
            val op = advance().text
            val operand = parseUnary()
            return Expr.Unary(op, operand)
        }
        return parsePrimary()
    }

    private fun parsePrimary(): Expr {
        val token = peek()

        if (match(TokenType.NUMBER)) {
            return Expr.Number(token.text.toFloatOrNull() ?: 0f)
        }
        if (match(TokenType.STRING)) {
            return Expr.StringLit(token.text)
        }
        if (token.text.lowercase(Locale.ROOT) == "true") {
            advance()
            return Expr.BoolLit(true)
        }
        if (token.text.lowercase(Locale.ROOT) == "false") {
            advance()
            return Expr.BoolLit(false)
        }

        if (token.type == TokenType.IDENTIFIER) {
            val id = advance().text
            if (check(TokenType.LPAREN)) {
                // Function call
                advance()
                val args = mutableListOf<NamedArg>()
                if (!check(TokenType.RPAREN)) {
                    do {
                        var argName: String? = null
                        if (pos + 1 < tokens.size && tokens[pos].type == TokenType.IDENTIFIER && tokens[pos + 1].type == TokenType.EQUAL) {
                            argName = advance().text
                            advance()
                        }
                        args.add(NamedArg(argName, parseExpression()))
                    } while (match(TokenType.COMMA))
                }
                consume(TokenType.RPAREN, "Expected ')'")
                return Expr.Call(id, args)
            }
            return Expr.Variable(id)
        }

        if (match(TokenType.LPAREN)) {
            val expr = parseExpression()
            consume(TokenType.RPAREN, "Expected ')'")
            return expr
        }

        if (match(TokenType.LBRACK)) {
            val elements = mutableListOf<Expr>()
            if (!check(TokenType.RBRACK)) {
                val first = parseExpression()
                if (match(TokenType.COLON)) {
                    // Range [start : end] or [start : step : end]
                    val second = parseExpression()
                    if (match(TokenType.COLON)) {
                        val third = parseExpression()
                        consume(TokenType.RBRACK, "Expected ']'")
                        return Expr.RangeExpr(first, second, third)
                    } else {
                        consume(TokenType.RBRACK, "Expected ']'")
                        return Expr.RangeExpr(first, null, second)
                    }
                } else {
                    elements.add(first)
                    while (match(TokenType.COMMA)) {
                        elements.add(parseExpression())
                    }
                }
            }
            consume(TokenType.RBRACK, "Expected ']'")
            return Expr.ArrayLit(elements)
        }

        parseErrors.add("Line ${token.line}: Unexpected token '${token.text}' in expression")
        advance()
        return Expr.Number(0f)
    }
}
