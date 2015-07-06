/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) {DATE}, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.hql.parser.antlr;

import org.hibernate.hql.parser.ParsingException;
import org.hibernate.hql.parser.semantic.expression.BinaryArithmeticExpression;
import org.hibernate.hql.parser.semantic.expression.ConcatExpression;
import org.hibernate.hql.parser.semantic.expression.Expression;
import org.hibernate.hql.parser.semantic.expression.ParameterNamedExpression;
import org.hibernate.hql.parser.semantic.expression.ParameterPositionalExpression;
import org.hibernate.hql.parser.semantic.expression.UnaryOperationExpression;

/**
 * @author Andrea Boriero
 */
public class ExpressionVisitor extends HqlParserBaseVisitor  {
	@Override
	public ConcatExpression visitConcatenationExpression(HqlParser.ConcatenationExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the concat operator" );
		}
		return new ConcatExpression(
				(Expression) ctx.expression( 0 ).accept( this ),
				(Expression) ctx.expression( 0 ).accept( this )
		);
	}

	@Override
	public Object visitAdditionExpression(HqlParser.AdditionExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the + operator" );
		}
		return new BinaryArithmeticExpression(
				BinaryArithmeticExpression.Operation.ADD,
				(Expression) ctx.expression( 0 ).accept( this ),
				(Expression) ctx.expression( 0 ).accept( this )
		);
	}

	@Override
	public Object visitSubtractionExpression(HqlParser.SubtractionExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the - operator" );
		}
		return new BinaryArithmeticExpression(
				BinaryArithmeticExpression.Operation.SUBTRACT,
				(Expression) ctx.expression( 0 ).accept( this ),
				(Expression) ctx.expression( 0 ).accept( this )
		);
	}

	@Override
	public Object visitMultiplicationExpression(HqlParser.MultiplicationExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the * operator" );
		}
		return new BinaryArithmeticExpression(
				BinaryArithmeticExpression.Operation.MULTIPLY,
				(Expression) ctx.expression( 0 ).accept( this ),
				(Expression) ctx.expression( 0 ).accept( this )
		);
	}

	@Override
	public Object visitDivisionExpression(HqlParser.DivisionExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the / operator" );
		}
		return new BinaryArithmeticExpression(
				BinaryArithmeticExpression.Operation.DIVIDE,
				(Expression) ctx.expression( 0 ).accept( this ),
				(Expression) ctx.expression( 0 ).accept( this )
		);
	}

	@Override
	public Object visitModuloExpression(HqlParser.ModuloExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the % operator" );
		}
		return new BinaryArithmeticExpression(
				BinaryArithmeticExpression.Operation.MODULO,
				(Expression) ctx.expression( 0 ).accept( this ),
				(Expression) ctx.expression( 0 ).accept( this )
		);
	}

	@Override
	public Object visitUnaryPlusExpression(HqlParser.UnaryPlusExpressionContext ctx) {
		return new UnaryOperationExpression(
				UnaryOperationExpression.Operation.PLUS,
				(Expression) ctx.expression().accept( this )
		);
	}

	@Override
	public Object visitUnaryMinusExpression(HqlParser.UnaryMinusExpressionContext ctx) {
		return new UnaryOperationExpression(
				UnaryOperationExpression.Operation.MINUS,
				(Expression) ctx.expression().accept( this )
		);
	}

	@Override
	public Object visitParameterExpression(HqlParser.ParameterExpressionContext ctx) {
		return ctx.parameter().accept( this );
	}

	@Override
	public ParameterNamedExpression visitNamedParameter(HqlParser.NamedParameterContext ctx) {
		return new ParameterNamedExpression( ctx.IDENTIFIER().getText() );
	}

	@Override
	public ParameterPositionalExpression visitPositionalParameter(HqlParser.PositionalParameterContext ctx) {
		return new ParameterPositionalExpression( Integer.valueOf( ctx.INTEGER_LITERAL().getText() ) );
	}
}
