/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 *  Copyright (c) 2012, Red Hat, Inc. and/or its affiliates or third-party contributors as
 *  indicated by the @author tags or express copyright attribution
 *  statements applied by the authors.  All third-party contributions are
 *  distributed under license by Red Hat, Inc.
 *
 *  This copyrighted material is made available to anyone wishing to use, modify,
 *  copy, or redistribute it subject to the terms and conditions of the GNU
 *  Lesser General Public License, as published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this distribution; if not, write to:
 *  Free Software Foundation, Inc.
 *  51 Franklin Street, Fifth Floor
 *  Boston, MA  02110-1301  USA
 */
package org.hibernate.search.test.integration.jms;

import static org.hibernate.search.test.integration.jms.util.RegistrationConfiguration.indexLocation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import javax.inject.Inject;

import org.hibernate.search.test.integration.jms.controller.RegistrationController;
import org.hibernate.search.test.integration.jms.controller.RegistrationMdb;
import org.hibernate.search.test.integration.jms.model.RegisteredMember;
import org.hibernate.search.test.integration.jms.util.RegistrationConfiguration;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.spec.jpa.persistence.PersistenceDescriptor;
import org.jboss.shrinkwrap.descriptor.api.spec.jpa.persistence.PersistenceUnitDef;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration test for hibernate search JMS Master/Slave configuration.
 *
 * @author Davide D'Alto
 * @author Sanne Grinovero
 */
@RunWith(Arquillian.class)
public class JmsMasterSlaveRegistrationIT {

	private static final int REFRESH_PERIOD_IN_SEC = 5;

	private static final int SLEEP_TIME_FOR_SYNCHRONIZATION = ( REFRESH_PERIOD_IN_SEC + 1 ) * 1000;

	private static final int MAX_SEARCH_ATTEMPTS = 3;

	@Deployment(name = "master", order = 1 )
	public static Archive<?> createDeploymentMaster() throws Exception {
		return baseArchive( "master", masterPersistenceXml() )
				.addClass( RegistrationMdb.class )
				.addAsWebInfResource( hornetqJmsXml(), "hornetq-jms.xml" )
				;
	}

	@Deployment(name = "slave-1", order = 2)
	public static Archive<?> createDeploymentSlave1() throws Exception {
		String name = "slave-1";
		return baseArchive( name, slavePersitenceXml( name ) );
	}

	@Deployment(name = "slave-2", order = 3)
	public static Archive<?> createDeploymentSlave2() throws Exception {
		String name = "slave-2";
		return baseArchive( name, slavePersitenceXml( name ) );
	}

	private static WebArchive baseArchive(String name, PersistenceUnitDef unitDef) throws Exception {
		return ShrinkWrap
				.create( WebArchive.class, name + ".war" )
				.addClasses( RegistrationController.class, RegisteredMember.class, RegistrationConfiguration.class )
				.addAsResource( new StringAsset( unitDef.exportAsString() ), "META-INF/persistence.xml" )
				.addAsLibraries( libraries() )
				.addAsWebInfResource( EmptyAsset.INSTANCE, "beans.xml" )
				;
	}

	private static PersistenceUnitDef masterPersistenceXml() throws Exception {
		return commonUnitDef( "pu-master", "filesystem-master" )
				.property( "hibernate.search.default.indexBase", indexLocation( "index-master" ) )
				;
	}

	private static PersistenceUnitDef slavePersitenceXml(String name) throws Exception {
		return commonUnitDef( "pu-" + name, "filesystem-slave" )
				.property( "hibernate.search.default.worker.backend", "jms" )
				.property( "hibernate.search.default.worker.jms.connection_factory", "ConnectionFactory" )
				.property( "hibernate.search.default.worker.jms.queue", RegistrationConfiguration.DESTINATION_QUEUE )
				.property( "hibernate.search.default.indexBase", indexLocation( "index-" + name ) )
				;
	}

	private static PersistenceUnitDef commonUnitDef(String unitName, String directoryProvider) throws Exception {
		return Descriptors.create( PersistenceDescriptor.class ).version( "2.0" )
				.persistenceUnit( unitName ).jtaDataSource( "java:jboss/datasources/ExampleDS" )
				.property( "hibernate.hbm2ddl.auto", "update" )
				.property( "hibernate.search.default.lucene_version", "LUCENE_CURRENT" )
				.property( "hibernate.search.default.directory_provider", directoryProvider )
				.property( "hibernate.search.default.sourceBase", indexLocation( "sourceBase" ) )
				.property( "hibernate.search.default.refresh", REFRESH_PERIOD_IN_SEC )
				.property( "hibernate.search.default.worker.execution", "sync" )
				;
	}

	private static Collection<JavaArchive> libraries() throws Exception {
		Collection<JavaArchive> libs = new ArrayList<JavaArchive>();
		File libDir = new File(getLibrariesDirectory());
		for ( String lib : libDir.list() ) {
			JavaArchive javaArchive = ShrinkWrap
					.create( ZipImporter.class, lib )
					.importFrom( new File( libDir, lib ) )
					.as( JavaArchive.class )
					;
			libs.add( javaArchive );
		}
		return libs;
	}

	private static Asset hornetqJmsXml() {
		String hornetqXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
								+ "<messaging-deployment xmlns=\"urn:jboss:messaging-deployment:1.0\">"
									+ "<hornetq-server>"
										+ "<jms-destinations>"
											+ "<jms-queue name=\"hsearchQueue\">"
												+ "<entry name=\"" + RegistrationConfiguration.DESTINATION_QUEUE + "\"/>"
											+ "</jms-queue>"
										+ "</jms-destinations>"
									+ "</hornetq-server>"
								+ "</messaging-deployment>";
		return new StringAsset( hornetqXml );
	}

	@Inject
	RegistrationController memberRegistration;

	@Test
	@InSequence(1)
	@OperateOnDeployment("slave-1")
	public void registerNewMember() throws Exception {
		RegisteredMember newMember = memberRegistration.getNewMember();
		assertNull( "A non registered member should have null ID", newMember.getId() );

		newMember.setName( "Davide D'Alto" );
		newMember.setEmail( "dd@slave1.fake.email" );
		memberRegistration.register();

		assertNotNull( "A registered member should have an ID", newMember.getId() );
	}

	@Test
	@InSequence(2)
	@OperateOnDeployment("master")
	public void searchNewMemberBeforeSynchronizationOnMaster() throws Exception {
		List<RegisteredMember> results = memberRegistration.search( "Davide" );
		assertEquals( "Result found before synchronization on Master", 0, results.size() );
	}

	@Test
	@InSequence(3)
	@OperateOnDeployment("slave-1")
	public void searchNewMemberBeforeSynchronizationOnSlave1() throws Exception {
		List<RegisteredMember> results = memberRegistration.search( "Davide" );
		assertEquals( "Result found before synchronization on Slave 1", 0, results.size() );
	}

	@Test
	@InSequence(4)
	@OperateOnDeployment("slave-2")
	public void searchNewMemberBeforeSynchronizationOnSlave2() throws Exception {
		List<RegisteredMember> results = memberRegistration.search( "Davide" );
		assertEquals( "Result found before synchronization on Slave 2", 0, results.size() );
	}

	@Test
	@InSequence(5)
	@OperateOnDeployment("slave-1")
	public void searchNewMemberAfterSynchronizationOnSlave1() throws Exception {
		List<RegisteredMember> results;
		int attempts = 0;
		do {
			attempts ++;
			waitForIndexSynchronization();
			results = memberRegistration.search( "Davide" );
		} while ( results.size() == 0 && attempts < MAX_SEARCH_ATTEMPTS );

		assertEquals( "Unexpected number of results from search", 1, results.size() );
		assertEquals( "Unexpected result from search", "Davide D'Alto", results.get( 0 ).getName() );
	}

	@Test
	@InSequence(6)
	@OperateOnDeployment("slave-2")
	public void searchNewMemberAfterSynchronizationOnSlave2() throws Exception {
		List<RegisteredMember> results = memberRegistration.search( "Davide" );

		assertEquals( "Unexpected number of results from search", 1, results.size() );
		assertEquals( "Unexpected result from search", "Davide D'Alto", results.get( 0 ).getName() );
	}

	@Test
	@InSequence(7)
	@OperateOnDeployment("master")
	public void searchNewMemberAfterSynchronizationOnMaster() throws Exception {
		List<RegisteredMember> results = memberRegistration.search( "Davide" );

		assertEquals( "Unexpected number of results from search", 1, results.size() );
		assertEquals( "Unexpected result from search", "Davide D'Alto", results.get( 0 ).getName() );
	}

	private void waitForIndexSynchronization() throws InterruptedException {
		Thread.sleep( SLEEP_TIME_FOR_SYNCHRONIZATION );
	}

	private static String getLibrariesDirectory() throws Exception {
		InputStream resourceAsStream = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream( "integration-test.properties" );
		Properties properties = new Properties();
		properties.load( resourceAsStream );
		return properties.getProperty( "lib.dir" );
	}

}
