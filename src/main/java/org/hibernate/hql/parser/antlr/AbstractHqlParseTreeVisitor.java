/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.hql.parser.antlr;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.jboss.logging.Logger;

import org.hibernate.hql.parser.NotYetImplementedException;
import org.hibernate.hql.parser.ParsingContext;
import org.hibernate.hql.parser.ParsingException;
import org.hibernate.hql.parser.SemanticException;
import org.hibernate.hql.parser.antlr.path.AttributePathPart;
import org.hibernate.hql.parser.antlr.path.AttributePathResolver;
import org.hibernate.hql.parser.model.EntityTypeDescriptor;
import org.hibernate.hql.parser.model.TypeDescriptor;
import org.hibernate.hql.parser.semantic.QuerySpec;
import org.hibernate.hql.parser.semantic.SelectStatement;
import org.hibernate.hql.parser.semantic.expression.ConstantEnumExpression;
import org.hibernate.hql.parser.semantic.expression.ConstantExpression;
import org.hibernate.hql.parser.semantic.expression.ConstantFieldExpression;
import org.hibernate.hql.parser.semantic.expression.EntityTypeExpression;
import org.hibernate.hql.parser.semantic.expression.Expression;
import org.hibernate.hql.parser.semantic.expression.FromElementReferenceExpression;
import org.hibernate.hql.parser.semantic.from.FromClause;
import org.hibernate.hql.parser.semantic.from.FromElement;
import org.hibernate.hql.parser.semantic.from.JoinedFromElement;
import org.hibernate.hql.parser.semantic.from.TreatedFromElement;
import org.hibernate.hql.parser.semantic.from.TreatedJoinedFromElement;
import org.hibernate.hql.parser.semantic.order.OrderByClause;
import org.hibernate.hql.parser.semantic.order.SortOrder;
import org.hibernate.hql.parser.semantic.order.SortSpecification;
import org.hibernate.hql.parser.semantic.predicate.Predicate;
import org.hibernate.hql.parser.semantic.predicate.WhereClause;
import org.hibernate.hql.parser.semantic.select.AliasedDynamicInstantiationArgument;
import org.hibernate.hql.parser.semantic.select.DynamicInstantiation;
import org.hibernate.hql.parser.semantic.select.SelectClause;
import org.hibernate.hql.parser.semantic.select.SelectList;
import org.hibernate.hql.parser.semantic.select.SelectListItem;
import org.hibernate.hql.parser.semantic.select.Selection;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractHqlParseTreeVisitor extends HqlParserBaseVisitor {
	private static final Logger log = Logger.getLogger( AbstractHqlParseTreeVisitor.class );

	private final ParsingContext parsingContext;

	public AbstractHqlParseTreeVisitor(ParsingContext parsingContext) {
		this.parsingContext = parsingContext;
	}

	public abstract FromClause getCurrentFromClause();

	public abstract AttributePathResolver getCurrentAttributePathResolver();


	@Override
	public SelectStatement visitSelectStatement(HqlParser.SelectStatementContext ctx) {
		final SelectStatement selectStatement = new SelectStatement( parsingContext );
		selectStatement.applyQuerySpec( visitQuerySpec( ctx.querySpec() ) );
		if ( ctx.orderByClause() != null ) {
			selectStatement.applyOrderByClause( visitOrderByClause( ctx.orderByClause() ) );
		}

		return selectStatement;
	}

	@Override
	public OrderByClause visitOrderByClause(HqlParser.OrderByClauseContext ctx) {
		final OrderByClause orderByClause = new OrderByClause( parsingContext );
		for ( HqlParser.SortSpecificationContext sortSpecificationContext : ctx.sortSpecification() ) {
			orderByClause.addSortSpecification( visitSortSpecification( sortSpecificationContext ) );
		}
		return orderByClause;
	}

	@Override
	public SortSpecification visitSortSpecification(HqlParser.SortSpecificationContext ctx) {
		final Expression sortExpression = (Expression) ctx.expression().accept( this );
		final String collation;
		if ( ctx.collationSpecification() != null && ctx.collationSpecification().collateName() != null ) {
			collation = ctx.collationSpecification().collateName().dotIdentifierSequence().getText();
		}
		else {
			collation = null;
		}
		final SortOrder sortOrder;
		if ( ctx.orderingSpecification() != null ) {
			sortOrder = SortOrder.interpret( ctx.orderingSpecification().getText() );
		}
		else {
			sortOrder = null;
		}
		return new SortSpecification( sortExpression, collation, sortOrder );
	}

	@Override
	public QuerySpec visitQuerySpec(HqlParser.QuerySpecContext ctx) {
		final SelectClause selectClause;
		if ( ctx.selectClause() != null ) {
			selectClause = visitSelectClause( ctx.selectClause() );
		}
		else {
			selectClause = buildInferredSelectClause( getCurrentFromClause() );
		}

		final WhereClause whereClause;
		if ( ctx.whereClause() != null ) {
			whereClause = visitWhereClause( ctx.whereClause() );
		}
		else {
			whereClause = null;
		}
		return new QuerySpec( parsingContext, getCurrentFromClause(), selectClause, whereClause );
	}

	protected SelectClause buildInferredSelectClause(FromClause fromClause) {
		// for now, this is slightly different than the legacy behavior where
		// the root and each non-fetched-join was selected.  For now, here, we simply
		// select the root
		return new SelectClause(
				new SelectList(
						new SelectListItem(
								new FromElementReferenceExpression(
										fromClause.getFromElementSpaces().get( 0 ).getRoot()
								)
						)
				)
		);
	}

	@Override
	public FromClause visitFromClause(HqlParser.FromClauseContext ctx) {
		return getCurrentFromClause();
	}

	@Override
	public SelectClause visitSelectClause(HqlParser.SelectClauseContext ctx) {
		return new SelectClause(
				visitSelection( ctx.selection() ),
				ctx.distinctKeyword() != null
		);
	}

	@Override
	public Selection visitSelection(HqlParser.SelectionContext ctx) {
		if ( ctx.dynamicInstantiation() != null ) {
			return visitDynamicInstantiation( ctx.dynamicInstantiation() );
		}
		else if ( ctx.jpaSelectObjectSyntax() != null ) {
			return visitJpaSelectObjectSyntax( ctx.jpaSelectObjectSyntax() );
		}
		else if ( ctx.selectItemList() != null ) {
			return visitSelectItemList( ctx.selectItemList() );
		}

		throw new ParsingException( "Unexpected selection rule type : " + ctx );
	}

	@Override
	public DynamicInstantiation visitDynamicInstantiation(HqlParser.DynamicInstantiationContext ctx) {
		final String className = ctx.dynamicInstantiationTarget().getText();
		final DynamicInstantiation dynamicInstantiation;
		try {
			dynamicInstantiation = new DynamicInstantiation(
					parsingContext.getConsumerContext().classByName( className )
			);
		}
		catch (ClassNotFoundException e) {
			throw new SemanticException( "Unable to resolve class named for dynamic instantiation : " + className );
		}

		for ( HqlParser.DynamicInstantiationArgContext arg : ctx.dynamicInstantiationArgs().dynamicInstantiationArg() ) {
			dynamicInstantiation.addArgument( visitDynamicInstantiationArg( arg ) );
		}

		return dynamicInstantiation;
	}

	@Override
	public AliasedDynamicInstantiationArgument visitDynamicInstantiationArg(HqlParser.DynamicInstantiationArgContext ctx) {
		return new AliasedDynamicInstantiationArgument(
				visitDynamicInstantiationArgExpression( ctx.dynamicInstantiationArgExpression() ),
				ctx.IDENTIFIER() == null ? null : ctx.IDENTIFIER().getText()
		);
	}

	@Override
	public Expression visitDynamicInstantiationArgExpression(HqlParser.DynamicInstantiationArgExpressionContext ctx) {
		if ( ctx.dynamicInstantiation() != null ) {
			return visitDynamicInstantiation( ctx.dynamicInstantiation() );
		}
		else if ( ctx.expression() != null ) {
			return (Expression) ctx.expression().accept( this );
		}

		throw new ParsingException( "Unexpected dynamic-instantiation-argument rule type : " + ctx );
	}

	@Override
	public Selection visitJpaSelectObjectSyntax(HqlParser.JpaSelectObjectSyntaxContext ctx) {
		final String alias = ctx.IDENTIFIER().getText();
		final FromElement fromElement = getCurrentFromClause().findFromElementByAlias( alias );
		if ( fromElement == null ) {
			throw new SemanticException( "Unable to resolve alias [" +  alias + "] in selection [" + ctx.getText() + "]" );
		}
		return new SelectList(
				new SelectListItem(
						new FromElementReferenceExpression( fromElement )
				)
		);
	}

	@Override
	public SelectList visitSelectItemList(HqlParser.SelectItemListContext ctx) {
		final SelectList selectList = new SelectList();
		for ( HqlParser.SelectItemContext selectItemContext : ctx.selectItem() ) {
			selectList.addSelectListItem( visitSelectItem( selectItemContext ) );
		}
		return selectList;
	}

	@Override
	public SelectListItem visitSelectItem(HqlParser.SelectItemContext ctx) {
		return new SelectListItem(
				(Expression) ctx.expression().accept( this ),
				ctx.IDENTIFIER() == null ? null : ctx.IDENTIFIER().getText()
		);
	}

	@Override
	public WhereClause visitWhereClause(HqlParser.WhereClauseContext ctx) {
		return new WhereClause( parsingContext, (Predicate) ctx.predicate().accept( this ) );
	}

	@Override
	public Object visitSimplePath(HqlParser.SimplePathContext ctx) {
		final AttributePathPart attributePathPart = getCurrentAttributePathResolver().resolvePath( ctx.dotIdentifierSequence() );
		if ( attributePathPart != null ) {
			return attributePathPart;
		}

		final String pathText = ctx.getText();

		final EntityTypeDescriptor entityType = parsingContext.getConsumerContext().resolveEntityReference( pathText );
		if ( entityType != null ) {
			return new EntityTypeExpression( entityType );
		}

		// 5th level precedence : constant reference
		try {
			return resolveConstantExpression( pathText );
		}
		catch (SemanticException e) {
			log.debug( e.getMessage() );
		}

		// if we get here we had a problem interpreting the dot-ident sequence
		throw new SemanticException( "Could not interpret token : " + pathText );
	}

	@SuppressWarnings("unchecked")
	protected ConstantExpression resolveConstantExpression(String reference) {
		// todo : hook in "import" resolution using the ParsingContext
		final int dotPosition = reference.lastIndexOf( '.' );
		final String className = reference.substring( 0, dotPosition - 1 );
		final String fieldName = reference.substring( dotPosition+1, reference.length() );

		try {
			final Class clazz = parsingContext.getConsumerContext().classByName( className );
			if ( clazz.isEnum() ) {
				try {
					return new ConstantEnumExpression( Enum.valueOf( clazz, fieldName ) );
				}
				catch (IllegalArgumentException e) {
					throw new SemanticException( "Name [" + fieldName + "] does not represent an enum constant on enum class [" + className + "]" );
				}
			}
			else {
				try {
					final Field field = clazz.getField( fieldName );
					if ( !Modifier.isStatic( field.getModifiers() ) ) {
						throw new SemanticException( "Field [" + fieldName + "] is not static on class [" + className + "]" );
					}
					field.setAccessible( true );
					return new ConstantFieldExpression( field.get( null ) );
				}
				catch (NoSuchFieldException e) {
					throw new SemanticException( "Name [" + fieldName + "] does not represent a field on class [" + className + "]", e );
				}
				catch (SecurityException e) {
					throw new SemanticException( "Field [" + fieldName + "] is not accessible on class [" + className + "]", e );
				}
				catch (IllegalAccessException e) {
					throw new SemanticException( "Unable to access field [" + fieldName + "] on class [" + className + "]", e );
				}
			}
		}
		catch (ClassNotFoundException e) {
			throw new SemanticException( "Cannot resolve class for query constant [" + reference + "]" );
		}
	}

	@Override
	public AttributePathPart visitTreatedPath(HqlParser.TreatedPathContext ctx) {
		final FromElement fromElement = (FromElement) getCurrentAttributePathResolver().resolvePath( ctx.dotIdentifierSequence().get( 0 ) );
		if ( fromElement == null ) {
			throw new SemanticException( "Could not resolve path [" + ctx.dotIdentifierSequence().get( 0 ).getText() + "] as base for TREAT-AS expression" );
		}

		final String treatAsName = ctx.dotIdentifierSequence().get( 1 ).getText();

		final TypeDescriptor treatAsTypeDescriptor = parsingContext.getConsumerContext().resolveEntityReference( treatAsName );
		if ( treatAsTypeDescriptor == null ) {
			throw new SemanticException( "TREAT-AS target type [" + treatAsName + "] did not reference an entity" );
		}

		fromElement.addTreatedAs( treatAsTypeDescriptor );

		if ( fromElement instanceof JoinedFromElement ) {
			return new TreatedJoinedFromElement( (JoinedFromElement) fromElement, treatAsTypeDescriptor );
		}
		else {
			return new TreatedFromElement( fromElement, treatAsTypeDescriptor );
		}
	}

	@Override
	public AttributePathPart visitIndexedPath(HqlParser.IndexedPathContext ctx) {
		final AttributePathPart source = (AttributePathPart) ctx.path().accept( this );
		// todo : how to resolve "expression" that defines the index value?

		throw new NotYetImplementedException();
	}
}
