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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.hibernate.hql.parser.LiteralNumberFormatException;
import org.hibernate.hql.parser.ParsingException;
import org.hibernate.hql.parser.semantic.expression.LiteralBigDecimalExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralBigIntegerExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralCharacterExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralDoubleExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralFalseExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralFloatExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralIntegerExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralLongExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralNullExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralStringExpression;
import org.hibernate.hql.parser.semantic.expression.LiteralTrueExpression;

/**
 * @author Andrea Boriero
 */
public class LiteralExpressionVisitor extends HqlParserBaseVisitor
{
	@Override
	@SuppressWarnings("UnnecessaryBoxing")
	public LiteralExpression visitLiteralExpression(HqlParser.LiteralExpressionContext ctx) {
		if ( ctx.literal().CHARACTER_LITERAL() != null ) {
			final String text = ctx.literal().CHARACTER_LITERAL().getText();
			if ( text.length() > 1 ) {
				// todo : or just treat it as a String literal?
				throw new ParsingException( "Value for CHARACTER_LITERAL token was more than 1 character" );
			}
			return new LiteralCharacterExpression( Character.valueOf( text.charAt( 0 ) ) );
		}
		else if ( ctx.literal().STRING_LITERAL() != null ) {
			return new LiteralStringExpression( ctx.literal().STRING_LITERAL().getText() );
		}
		else if ( ctx.literal().INTEGER_LITERAL() != null ) {
			return integerLiteral( ctx.literal().INTEGER_LITERAL().getText() );
		}
		else if ( ctx.literal().LONG_LITERAL() != null ) {
			return longLiteral( ctx.literal().LONG_LITERAL().getText() );
		}
		else if ( ctx.literal().BIG_INTEGER_LITERAL() != null ) {
			return bigIntegerLiteral( ctx.literal().BIG_INTEGER_LITERAL().getText() );
		}
		else if ( ctx.literal().HEX_LITERAL() != null ) {
			final String text = ctx.literal().HEX_LITERAL().getText();
			if ( text.endsWith( "l" ) || text.endsWith( "L" ) ) {
				return longLiteral( text );
			}
			else {
				return integerLiteral( text );
			}
		}
		else if ( ctx.literal().OCTAL_LITERAL() != null ) {
			final String text = ctx.literal().OCTAL_LITERAL().getText();
			if ( text.endsWith( "l" ) || text.endsWith( "L" ) ) {
				return longLiteral( text );
			}
			else {
				return integerLiteral( text );
			}
		}
		else if ( ctx.literal().FLOAT_LITERAL() != null ) {
			return floatLiteral( ctx.literal().FLOAT_LITERAL().getText() );
		}
		else if ( ctx.literal().DOUBLE_LITERAL() != null ) {
			return doubleLiteral( ctx.literal().DOUBLE_LITERAL().getText() );
		}
		else if ( ctx.literal().BIG_DECIMAL_LITERAL() != null ) {
			return bigDecimalLiteral( ctx.literal().BIG_DECIMAL_LITERAL().getText() );
		}
		else if ( ctx.literal().FALSE() != null ) {
			return new LiteralFalseExpression();
		}
		else if ( ctx.literal().TRUE() != null ) {
			return new LiteralTrueExpression();
		}
		else if ( ctx.literal().NULL() != null ) {
			return new LiteralNullExpression();
		}

		// otherwise we have a problem
		throw new ParsingException( "Unexpected literal expression type [" + ctx.getText() + "]" );
	}

	protected LiteralIntegerExpression integerLiteral(String text) {
		try {
			final Integer value = Integer.valueOf( text );
			return new LiteralIntegerExpression( value );
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert query literal [" + text + "] to Integer",
					e
			);
		}
	}

	protected LiteralLongExpression longLiteral(String text) {
		final String originalText = text;
		try {
			if ( text.endsWith( "l" ) || text.endsWith( "L" ) ) {
				text = text.substring( 0, text.length() - 1 );
			}
			final Long value = Long.valueOf( text );
			return new LiteralLongExpression( value );
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert query literal [" + originalText + "] to Long",
					e
			);
		}
	}

	protected LiteralBigIntegerExpression bigIntegerLiteral(String text) {
		final String originalText = text;
		try {
			if ( text.endsWith( "bi" ) || text.endsWith( "BI" ) ) {
				text = text.substring( 0, text.length() - 2 );
			}
			return new LiteralBigIntegerExpression( new BigInteger( text ) );
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert query literal [" + originalText + "] to BigInteger",
					e
			);
		}
	}

	protected LiteralFloatExpression floatLiteral(String text) {
		try {
			return new LiteralFloatExpression( Float.valueOf( text ) );
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert query literal [" + text + "] to Float",
					e
			);
		}
	}

	protected LiteralDoubleExpression doubleLiteral(String text) {
		try {
			return new LiteralDoubleExpression( Double.valueOf( text ) );
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert query literal [" + text + "] to Double",
					e
			);
		}
	}

	protected LiteralBigDecimalExpression bigDecimalLiteral(String text) {
		final String originalText = text;
		try {
			if ( text.endsWith( "bd" ) || text.endsWith( "BD" ) ) {
				text = text.substring( 0, text.length() - 2 );
			}
			return new LiteralBigDecimalExpression( new BigDecimal( text ) );
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert query literal [" + originalText + "] to BigDecimal",
					e
			);
		}
	}
}
