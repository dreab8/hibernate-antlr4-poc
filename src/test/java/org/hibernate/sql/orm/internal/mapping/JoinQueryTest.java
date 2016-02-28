/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.sql.orm.internal.mapping;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.boot.MetadataSources;
import org.hibernate.persister.entity.JoinedSubclassEntityPersister;
import org.hibernate.persister.entity.SingleTableEntityPersister;
import org.hibernate.sql.ast.QuerySpec;
import org.hibernate.sql.ast.from.EntityTableSpecificationGroup;
import org.hibernate.sql.ast.from.PhysicalTableSpecification;
import org.hibernate.sql.gen.BaseUnitTest;
import org.hibernate.sql.gen.internal.FromClauseIndex;
import org.hibernate.sql.gen.internal.SqlAliasBaseManager;
import org.hibernate.sql.orm.internal.sqm.model.EntityTypeImpl;
import org.hibernate.sqm.query.SelectStatement;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Andrea Boriero
 */
public class JoinQueryTest extends BaseUnitTest {

	@Test
	public void testJoinQuery() {
		SelectStatement sqm = (SelectStatement) interpret( "from Person p join p.addresses" );

		final EntityTypeImpl entityTypeDescriptor =
				(EntityTypeImpl) getConsumerContext().getDomainMetamodel().resolveEntityType( "Person" );
		final ImprovedEntityPersister improvedEntityPersister = entityTypeDescriptor.getPersister();
		assertThat( improvedEntityPersister.getEntityPersister(), instanceOf( SingleTableEntityPersister.class ) );

		// interpreter set up
		final QuerySpec querySpec = new QuerySpec();
		final SqlAliasBaseManager aliasBaseManager = new SqlAliasBaseManager();
		final FromClauseIndex fromClauseIndex = new FromClauseIndex();

		final EntityTableSpecificationGroup result = improvedEntityPersister.getEntityTableSpecificationGroup(
				sqm.getQuerySpec().getFromClause().getFromElementSpaces().get( 0 ).getRoot(),
				querySpec.getFromClause().makeTableSpace(),
				aliasBaseManager,
				fromClauseIndex
		);
		assertThat( result, notNullValue() );
		assertThat( result.getAliasBase(), equalTo( "p1" ) );

		assertThat( result.getRootTableSpecification(), notNullValue() );
		assertThat( result.getRootTableSpecification(), instanceOf( PhysicalTableSpecification.class ) );
		final PhysicalTableSpecification tableSpec = (PhysicalTableSpecification) result.getRootTableSpecification();
		assertThat( tableSpec.getTableName(), equalTo( "PERSON" ) );

		assertThat( result.getTableSpecificationJoins().size(), equalTo( 2 ) );



	}

	@Override
	protected void applyMetadataSources(MetadataSources metadataSources) {
		metadataSources.addAnnotatedClass( Person.class );
		metadataSources.addAnnotatedClass( Address.class );
	}

	@Entity(name = "Person")
	@Table(name = "PERSON")
	public static class Person {
		@Id
		@GeneratedValue
		long id;

		@OneToMany
		Set<Address> addresses = new HashSet<>();
	}

	@Entity(name = "Address")
	@Table(name = "ADDRESS")
	public static class Address {
		@Id
		@GeneratedValue
		long id;
	}
}
