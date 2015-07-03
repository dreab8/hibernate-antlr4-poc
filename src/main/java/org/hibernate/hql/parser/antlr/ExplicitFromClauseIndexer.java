/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.hql.parser.antlr;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;

import org.hibernate.hql.parser.JoinType;
import org.hibernate.hql.parser.ParsingContext;
import org.hibernate.hql.parser.ParsingException;
import org.hibernate.hql.parser.SemanticException;
import org.hibernate.hql.parser.antlr.path.AttributePathPart;
import org.hibernate.hql.parser.antlr.path.AttributePathResolver;
import org.hibernate.hql.parser.antlr.path.AttributePathResolverStack;
import org.hibernate.hql.parser.antlr.path.BasicAttributePathResolverImpl;
import org.hibernate.hql.parser.model.AttributeDescriptor;
import org.hibernate.hql.parser.model.EntityTypeDescriptor;
import org.hibernate.hql.parser.semantic.Statement;
import org.hibernate.hql.parser.semantic.expression.ConstantEnumExpression;
import org.hibernate.hql.parser.semantic.expression.ConstantExpression;
import org.hibernate.hql.parser.semantic.expression.ConstantFieldExpression;
import org.hibernate.hql.parser.semantic.expression.EntityTypeExpression;
import org.hibernate.hql.parser.semantic.from.FromClause;
import org.hibernate.hql.parser.semantic.from.FromElement;
import org.hibernate.hql.parser.semantic.from.FromElementSpace;
import org.hibernate.hql.parser.semantic.from.JoinedFromElement;

/**
 * @author Steve Ebersole
 */
public class ExplicitFromClauseIndexer extends HqlParserBaseListener {
	private final ParsingContext parsingContext;
	private final FromClause rootFromClause;

	private Statement.Type statementType;

	// Using HqlParser.QuerySpecContext direct did not work in my experience, each walk
	// seems to build new instances.  So use the context text as key :(
	private final Map<String, FromClause> fromClauseMap = new HashMap<String, FromClause>();
	// for the same reason seems not working the ParseTreeProperty and so the
	// node annotations during successive walks
	private ParseTreeProperty<FromClause> fromClauseValues = new ParseTreeProperty<>();
	private ParseTreeProperty<JoinedFromElement> joinedFromElementValues = new ParseTreeProperty<>();
	private ParseTreeProperty<AttributePathResolver> attributePathResolvers = new ParseTreeProperty<>();

	final AttributePathResolverStack resolverStack = new AttributePathResolverStack();

	public ExplicitFromClauseIndexer(ParsingContext parsingContext) {
		this.parsingContext = parsingContext;
		this.rootFromClause = new FromClause( parsingContext );
	}

	public Statement.Type getStatementType() {
		return statementType;
	}

	public FromClause getRootFromClause() {
		return rootFromClause;
	}

	public FromClause findFromClauseForQuerySpec(HqlParser.QuerySpecContext ctx) {
		if ( getFromClauseValue( ctx ) != null ) {
			return getFromClauseValue( ctx );
		}
		return fromClauseMap.get( ctx.getText() );
	}

	@Override
	public void enterSelectStatement(HqlParser.SelectStatementContext ctx) {
		statementType = Statement.Type.SELECT;
	}

	@Override
	public void enterInsertStatement(HqlParser.InsertStatementContext ctx) {
		statementType = Statement.Type.INSERT;
	}

	@Override
	public void enterUpdateStatement(HqlParser.UpdateStatementContext ctx) {
		statementType = Statement.Type.UPDATE;
	}

	@Override
	public void enterDeleteStatement(HqlParser.DeleteStatementContext ctx) {
		statementType = Statement.Type.DELETE;
	}

	private FromClause currentFromClause;

	@Override
	public void enterFromClause(HqlParser.FromClauseContext ctx) {
		super.enterFromClause( ctx );

		if ( currentFromClause == null ) {
			currentFromClause = rootFromClause;
		}
		else {
			currentFromClause = currentFromClause.makeChildFromClause();
		}
	}

	@Override
	public void exitQuerySpec(HqlParser.QuerySpecContext ctx) {
		this.setFromClauseValue( ctx, currentFromClause );

		if ( currentFromClause == null ) {
			throw new IllegalStateException( "Mismatch currentFromClause handling" );
		}
		currentFromClause = currentFromClause.getParentFromClause();
	}

//	@Override
//	public void exitFromClause(HqlParser.FromClauseContext ctx) {
//		if ( currentFromClause == null ) {
//			throw new IllegalStateException( "Mismatch currentFromClause handling" );
//		}
//		currentFromClause = currentFromClause.getParentFromClause();
//	}

	private FromElementSpace currentFromElementSpace;

	@Override
	public void enterFromElementSpace(HqlParser.FromElementSpaceContext ctx) {
		currentFromElementSpace = currentFromClause.makeFromElementSpace();
	}

	@Override
	public void exitFromElementSpace(HqlParser.FromElementSpaceContext ctx) {
		currentFromElementSpace.complete();
		currentFromElementSpace = null;
	}

	@Override
	public void enterRootEntityReference(HqlParser.RootEntityReferenceContext ctx) {
		currentFromElementSpace.makeRootEntityFromElement(
				resolveEntityReference( ctx.mainEntityPersisterReference().dotIdentifierSequence() ),
				interpretAlias( ctx.mainEntityPersisterReference().IDENTIFIER() )
		);
	}

	private EntityTypeDescriptor resolveEntityReference(HqlParser.DotIdentifierSequenceContext dotIdentifierSequenceContext) {
		final String entityName = dotIdentifierSequenceContext.getText();
		final EntityTypeDescriptor entityTypeDescriptor = parsingContext.getConsumerContext().resolveEntityReference(
				entityName
		);
		if ( entityTypeDescriptor == null ) {
			throw new SemanticException( "Unresolved entity name : " + entityName );
		}
		return entityTypeDescriptor;
	}

	private String interpretAlias(TerminalNode aliasNode) {
		if ( aliasNode == null ) {
			return parsingContext.getImplicitAliasGenerator().buildUniqueImplicitAlias();
		}
		assert aliasNode.getSymbol().getType() == HqlParser.IDENTIFIER;
		return aliasNode.getText();
	}

	@Override
	public void enterCrossJoin(HqlParser.CrossJoinContext ctx) {
		currentFromElementSpace.makeCrossJoinedFromElement(
				resolveEntityReference( ctx.mainEntityPersisterReference().dotIdentifierSequence() ),
				interpretAlias( ctx.mainEntityPersisterReference().IDENTIFIER() )
		);
	}

	@Override
	public void enterExplicitInnerJoin(HqlParser.ExplicitInnerJoinContext ctx) {
//		final QualifiedJoinTreeVisitor visitor = new QualifiedJoinTreeVisitor(
//				parsingContext,
//				currentFromElementSpace,
//				JoinType.INNER,
//				interpretAlias( ctx.qualifiedJoinRhs().IDENTIFIER() ),
//				ctx.fetchKeyword() != null
//		);
//
//		JoinedFromElement joinedPath = (JoinedFromElement) ctx.qualifiedJoinRhs().path().accept( visitor );
//
//		if ( joinedPath == null ) {
//			throw new ParsingException( "Could not resolve join path : " + ctx.qualifiedJoinRhs().getText() );
//		}

		// we'll handle on-clause predicates in a later pass
	}

	@Override
	public void enterExplicitOuterJoin(HqlParser.ExplicitOuterJoinContext ctx) {
		setAttributePathResolver(
				ctx.qualifiedJoinRhs(), new JoinAttributePathResolver(
						currentFromElementSpace,
						JoinType.LEFT,
						interpretAlias(
								ctx.qualifiedJoinRhs()
										.IDENTIFIER()
						),
						ctx.fetchKeyword() != null
				)
		);

		// we'll handle on-clause predicates in a later pass
	}


	@Override
	public void exitQualifiedJoinRhs(HqlParser.QualifiedJoinRhsContext ctx) {
		JoinedFromElement joinedPath = getJoinedFromElementValue( ctx.path() );

		if ( joinedPath == null ) {
			throw new ParsingException( "Could not resolve join path : " + ctx.path() );
		}
	}

	@Override
	public void exitSimplePath(HqlParser.SimplePathContext ctx) {
		AttributePathResolver attributePathResolver = getAttributePathResolver( ctx.getParent() );

		if ( attributePathResolver != null ) {
			final AttributePathPart attributePathPart = attributePathResolver.resolvePath( ctx.dotIdentifierSequence() );
			if ( attributePathPart != null ) {
				setJoinedFromElementValue( ctx, (JoinedFromElement) attributePathPart );
			}
		}
		else {

			final String pathText = ctx.getText();

			final EntityTypeDescriptor entityType = parsingContext.getConsumerContext()
					.resolveEntityReference( pathText );
			if ( entityType != null ) {
				EntityTypeExpression entityTypeExpression = new EntityTypeExpression( entityType );
			}
			else {
				// 5th level precedence : constant reference
				ConstantExpression constantExpression = resolveConstantExpression( pathText );
			}
		}
	}

	protected ConstantExpression resolveConstantExpression(String reference) {
		// todo : hook in "import" resolution using the ParsingContext
		final int dotPosition = reference.lastIndexOf( '.' );
		final String className = reference.substring( 0, dotPosition - 1 );
		final String fieldName = reference.substring( dotPosition + 1, reference.length() );

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
					throw new SemanticException(
							"Name [" + fieldName + "] does not represent a field on class [" + className + "]",
							e
					);
				}
				catch (SecurityException e) {
					throw new SemanticException(
							"Field [" + fieldName + "] is not accessible on class [" + className + "]",
							e
					);
				}
				catch (IllegalAccessException e) {
					throw new SemanticException(
							"Unable to access field [" + fieldName + "] on class [" + className + "]",
							e
					);
				}
			}
		}
		catch (ClassNotFoundException e) {
			throw new SemanticException( "Cannot resolve class for query constant [" + reference + "]" );
		}
	}

	private static class JoinAttributePathResolver extends BasicAttributePathResolverImpl {
		private final FromElementSpace fromElementSpace;
		private final JoinType joinType;
		private final String alias;
		private final boolean fetched;

		public JoinAttributePathResolver(
				FromElementSpace fromElementSpace,
				JoinType joinType,
				String alias,
				boolean fetched) {
			super( fromElementSpace.getFromClause() );
			this.fromElementSpace = fromElementSpace;
			this.joinType = joinType;
			this.alias = alias;
			this.fetched = fetched;
		}

		@Override
		protected JoinType getIntermediateJoinType() {
			return joinType;
		}

		protected boolean areIntermediateJoinsFetched() {
			return fetched;
		}

		@Override
		protected AttributePathPart resolveTerminalPathPart(FromElement lhs, String terminalName) {
			return fromElementSpace.buildAttributeJoin(
					lhs,
					resolveAttributeDescriptor( lhs, terminalName ),
					alias,
					joinType,
					fetched
			);
		}

		protected AttributeDescriptor resolveAttributeDescriptor(FromElement lhs, String attributeName) {
			final AttributeDescriptor attributeDescriptor = lhs.getTypeDescriptor().getAttributeDescriptor(
					attributeName
			);
			if ( attributeDescriptor == null ) {
				throw new SemanticException(
						"Name [" + attributeName + "] is not a valid attribute on from-element [" +
								lhs.getTypeDescriptor().getTypeName() + "]"
				);
			}

			return attributeDescriptor;
		}

		@Override
		protected AttributePathPart resolveFromElementAliasAsTerminal(FromElement aliasedFromElement) {
			return aliasedFromElement;
		}
	}

	protected void setFromClauseValue(ParseTree node, FromClause value) {
		System.out.println( "Set node hashCode : " + node.hashCode() + " , name " + node.getText() );
		this.fromClauseValues.put( node, value );
		this.fromClauseMap.put( node.getText(), value );
	}

	protected FromClause getFromClauseValue(ParseTree node) {
		System.out.println( "get node hashCode : " + node.hashCode() + " , name " + node.getText() );
		return this.fromClauseValues.get( node );
	}

	public JoinedFromElement getJoinedFromElementValue(ParseTree node) {
		return joinedFromElementValues.get( node );
	}

	public void setJoinedFromElementValue(ParseTree node, JoinedFromElement joinedFromElementValue) {
		this.joinedFromElementValues.put( node, joinedFromElementValue );
	}

	public AttributePathResolver getAttributePathResolver(ParseTree node) {
		return attributePathResolvers.get( node );
	}

	public void setAttributePathResolver(ParseTree node, AttributePathResolver attributePathResolver) {
		this.attributePathResolvers.put( node, attributePathResolver );
	}
}
