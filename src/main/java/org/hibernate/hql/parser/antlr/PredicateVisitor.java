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

import org.hibernate.hql.parser.SemanticException;
import org.hibernate.hql.parser.model.CollectionTypeDescriptor;
import org.hibernate.hql.parser.semantic.expression.AttributeReferenceExpression;
import org.hibernate.hql.parser.semantic.expression.Expression;
import org.hibernate.hql.parser.semantic.predicate.AndPredicate;
import org.hibernate.hql.parser.semantic.predicate.BetweenPredicate;
import org.hibernate.hql.parser.semantic.predicate.GroupedPredicate;
import org.hibernate.hql.parser.semantic.predicate.IsEmptyPredicate;
import org.hibernate.hql.parser.semantic.predicate.IsNullPredicate;
import org.hibernate.hql.parser.semantic.predicate.LikePredicate;
import org.hibernate.hql.parser.semantic.predicate.MemberOfPredicate;
import org.hibernate.hql.parser.semantic.predicate.NegatedPredicate;
import org.hibernate.hql.parser.semantic.predicate.OrPredicate;
import org.hibernate.hql.parser.semantic.predicate.Predicate;
import org.hibernate.hql.parser.semantic.predicate.RelationalPredicate;

/**
 * @author Andrea Boriero
 */
public class PredicateVisitor extends HqlParserBaseVisitor {
	@Override
	public GroupedPredicate visitGroupedPredicate(HqlParser.GroupedPredicateContext ctx) {
		return new GroupedPredicate( (Predicate) ctx.predicate().accept( this ) );
	}

	@Override
	public AndPredicate visitAndPredicate(HqlParser.AndPredicateContext ctx) {
		return new AndPredicate(
				(Predicate) ctx.predicate( 0 ).accept( this ),
				(Predicate) ctx.predicate( 1 ).accept( this )
		);
	}

	@Override
	public OrPredicate visitOrPredicate(HqlParser.OrPredicateContext ctx) {
		return new OrPredicate(
				(Predicate) ctx.predicate( 0 ).accept( this ),
				(Predicate) ctx.predicate( 1 ).accept( this )
		);
	}

	@Override
	public NegatedPredicate visitNegatedPredicate(HqlParser.NegatedPredicateContext ctx) {
		return new NegatedPredicate( (Predicate) ctx.predicate().accept( this ) );
	}

	@Override
	public IsNullPredicate visitIsNullPredicate(HqlParser.IsNullPredicateContext ctx) {
		return new IsNullPredicate( (Expression) ctx.expression().accept( this ) );
	}

	@Override
	public IsEmptyPredicate visitIsEmptyPredicate(HqlParser.IsEmptyPredicateContext ctx) {
		return new IsEmptyPredicate( (Expression) ctx.expression().accept( this ) );
	}

	@Override
	public Object visitEqualityPredicate(HqlParser.EqualityPredicateContext ctx) {
		return new RelationalPredicate(
				RelationalPredicate.Type.EQUAL,
				(Expression) ctx.expression().get( 0 ).accept( this ),
				(Expression) ctx.expression().get( 1 ).accept( this )
		);
	}

	@Override
	public Object visitInequalityPredicate(HqlParser.InequalityPredicateContext ctx) {
		return new RelationalPredicate(
				RelationalPredicate.Type.NOT_EQUAL,
				(Expression) ctx.expression().get( 0 ).accept( this ),
				(Expression) ctx.expression().get( 1 ).accept( this )
		);
	}

	@Override
	public Object visitGreaterThanPredicate(HqlParser.GreaterThanPredicateContext ctx) {
		return new RelationalPredicate(
				RelationalPredicate.Type.GT,
				(Expression) ctx.expression().get( 0 ).accept( this ),
				(Expression) ctx.expression().get( 1 ).accept( this )
		);
	}

	@Override
	public Object visitGreaterThanOrEqualPredicate(HqlParser.GreaterThanOrEqualPredicateContext ctx) {
		return new RelationalPredicate(
				RelationalPredicate.Type.GE,
				(Expression) ctx.expression().get( 0 ).accept( this ),
				(Expression) ctx.expression().get( 1 ).accept( this )
		);
	}

	@Override
	public Object visitLessThanPredicate(HqlParser.LessThanPredicateContext ctx) {
		return new RelationalPredicate(
				RelationalPredicate.Type.LT,
				(Expression) ctx.expression().get( 0 ).accept( this ),
				(Expression) ctx.expression().get( 1 ).accept( this )
		);
	}

	@Override
	public Object visitLessThanOrEqualPredicate(HqlParser.LessThanOrEqualPredicateContext ctx) {
		return new RelationalPredicate(
				RelationalPredicate.Type.LE,
				(Expression) ctx.expression().get( 0 ).accept( this ),
				(Expression) ctx.expression().get( 1 ).accept( this )
		);
	}

	@Override
	public Object visitBetweenPredicate(HqlParser.BetweenPredicateContext ctx) {
		return new BetweenPredicate(
				(Expression) ctx.expression().get( 0 ).accept( this ),
				(Expression) ctx.expression().get( 1 ).accept( this ),
				(Expression) ctx.expression().get( 2 ).accept( this )
		);
	}

	@Override
	public Object visitLikePredicate(HqlParser.LikePredicateContext ctx) {
		if ( ctx.likeEscape() != null ) {
			return new LikePredicate(
					(Expression) ctx.expression().get( 0 ).accept( this ),
					(Expression) ctx.expression().get( 1 ).accept( this ),
					(Expression) ctx.likeEscape().expression().accept( this )
			);
		}
		else {
			return new LikePredicate(
					(Expression) ctx.expression().get( 0 ).accept( this ),
					(Expression) ctx.expression().get( 1 ).accept( this )
			);
		}
	}

	@Override
	public Object visitMemberOfPredicate(HqlParser.MemberOfPredicateContext ctx) {
		final Object pathResolution = ctx.path().accept( this );
		if ( !AttributeReferenceExpression.class.isInstance( pathResolution ) ) {
			throw new SemanticException( "Could not resolve path [" + ctx.path().getText() + "] as an attribute reference" );
		}
		final AttributeReferenceExpression attributeReference = (AttributeReferenceExpression) pathResolution;
		if ( !CollectionTypeDescriptor.class.isInstance( attributeReference.getTypeDescriptor() ) ) {
			throw new SemanticException( "Path argument to MEMBER OF must be a collection" );
		}
		return new MemberOfPredicate( attributeReference );
	}
}
