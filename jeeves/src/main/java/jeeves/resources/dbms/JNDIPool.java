//=============================================================================
//=== Copyright (C) 2001-2011 Food and Agriculture Organization of the
//=== United Nations (FAO-UN), United Nations World Food Programme (WFP)
//=== and United Nations Environment Programme (UNEP)
//===
//===	This library is free software; you can redistribute it and/or
//===	modify it under the terms of the GNU Lesser General Public
//===	License as published by the Free Software Foundation; either
//===	version 2.1 of the License, or (at your option) any later version.
//===
//===	This library is distributed in the hope that it will be useful,
//===	but WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//===	Lesser General Public License for more details.
//===
//===	You should have received a copy of the GNU Lesser General Public
//===	License along with this library; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
//===
//===	Contact: Jeroen Ticheler - GeoCat
//===	email: Jeroen.Ticheler@geocat.org
//==============================================================================

package jeeves.resources.dbms;

import java.util.HashMap;
import java.util.Map;
import java.sql.SQLException;

import jeeves.constants.Jeeves;

import jeeves.server.resources.Stats;
import org.jdom.Element;

import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.data.db2.DB2NGJNDIDataStoreFactory;
import org.geotools.data.h2.H2JNDIDataStoreFactory;
import org.geotools.data.mysql.MySQLJNDIDataStoreFactory;
import org.geotools.data.oracle.OracleNGJNDIDataStoreFactory;
import org.geotools.data.postgis.PostgisNGJNDIDataStoreFactory;
import org.geotools.data.postgis.PostgisNGDataStoreFactory;
import org.geotools.data.sqlserver.SQLServerJNDIDataStoreFactory;
import org.geotools.data.DataStore;

//=============================================================================

/**
 * A pool of database connections via JNDI 
 */

public class JNDIPool extends AbstractDbmsPool {

	private JDBCDataStore dataStore;
	private static final String UNKOWN_PARAM = "unknown - JNDI data source supplied by container";

	// --------------------------------------------------------------------------
	// ---
	// --- API
	// ---
	// --------------------------------------------------------------------------

	/**
	 * Builds the pool using JNDI context.
	 */
	public void init(String name, Element config) throws Exception {

		// check and see whether we have a JNDI context defined

		String contextName = config.getChildText(Jeeves.Res.Pool.CONTEXT);
		boolean provideDataStore = false; 

		if (contextName != null) {
			String dsName      = config.getChildText(Jeeves.Res.Pool.RESOURCE_NAME);
			if (dsName == null) throw new IllegalArgumentException("context "+contextName+" specified but resource name parameter "+Jeeves.Res.Pool.RESOURCE_NAME+" has not been found");
			url                = config.getChildText(Jeeves.Res.Pool.URL);
			if (url == null) throw new IllegalArgumentException("context "+contextName+" specified with resource name parameter "+dsName+" but "+Jeeves.Res.Pool.URL+" not found");

			this.name = url;

			int iMaxOpen = -1;

			Map<String,String> params = new HashMap<String,String>();

			String uTest = url.toLowerCase();
			String dbType = JDBCDataStoreFactory.DBTYPE.key;
			params.put("jndiReferenceName", contextName + "/" + dsName);

			String provideDataStoreStr = config.getChildText(Jeeves.Res.Pool.PROVIDE_DATA_STORE);
			if (provideDataStoreStr != null) {
				provideDataStore = provideDataStoreStr.equals("true");
			}

			iMaxOpen = getPreparedStatementCacheSize(config);
			if (iMaxOpen != -1) {
				params.put(JDBCDataStoreFactory.MAX_OPEN_PREPARED_STATEMENTS.key, iMaxOpen+"");
			}

			if (uTest.contains("db2")) { 
				params.put(dbType, "db2");
				DB2NGJNDIDataStoreFactory factory = new DB2NGJNDIDataStoreFactory();
				dataStore = (JDBCDataStore)factory.createDataStore(params);
			} else if (uTest.contains("postgis")) {
				params.put(dbType, "postgis");
				if (iMaxOpen != -1) {
					params.put(PostgisNGDataStoreFactory.PREPARED_STATEMENTS.key, "true");
				}
				PostgisNGJNDIDataStoreFactory factory = new PostgisNGJNDIDataStoreFactory();
				dataStore = (JDBCDataStore)factory.createDataStore(params);
			} else if (uTest.contains("oracle")) {
				params.put(dbType, "oracle");
				OracleNGJNDIDataStoreFactory factory = new OracleNGJNDIDataStoreFactory();
				dataStore = (JDBCDataStore)factory.createDataStore(params);
			} else if (uTest.contains("mysql")) {
				params.put(dbType, "mysql");
				MySQLJNDIDataStoreFactory factory = new MySQLJNDIDataStoreFactory();
				dataStore = (JDBCDataStore)factory.createDataStore(params);
			} else if (uTest.contains("sqlserver")) {
				params.put(dbType, "sqlserver");
				SQLServerJNDIDataStoreFactory factory = new SQLServerJNDIDataStoreFactory();
				dataStore = (JDBCDataStore)factory.createDataStore(params);
			} else if (uTest.contains("h2")) {
				params.put(dbType, "h2");
				H2JNDIDataStoreFactory factory = new H2JNDIDataStoreFactory();
				dataStore = (JDBCDataStore)factory.createDataStore(params);
			} else {
				throw new IllegalArgumentException("unknown database vendor in "+Jeeves.Res.Pool.URL+" parameter: "+url);
			}

			if (dataStore == null) {
				throw new IllegalArgumentException("cannot find datastore that can process params: "+params);
			}
		} else {
			throw new IllegalArgumentException("context name parameter "+Jeeves.Res.Pool.CONTEXT+" was not found");
		}
		debug(toString());
		if (provideDataStore) setDataStore((DataStore)dataStore);
		setDataSource(dataStore.getDataSource());
	}

	// --------------------------------------------------------------------------

	public Map<String, String> getProps() {
		Map<String, String> result = new HashMap<String, String>();
		result.put("name", name);
		result.put("user", 			UNKOWN_PARAM);
		result.put("password",	UNKOWN_PARAM);
		result.put("url", url);
		return result;
	}

	// --------------------------------------------------------------------------

	public Stats getStats() throws SQLException {
		return new Stats(null,null,null);
	}

	// --------------------------------------------------------------------------

	public void end() {
		debug("Disposing datastore");
		dataStore.dispose();
	}

	// --------------------------------------------------------------------------

	public String toString() {
		StringBuffer sb = new StringBuffer();

		sb.append("------------------------------------------------------------------------------");
		sb.append('\n');
		sb.append("Created JNDI connection pool (" + this.name + ")");
		sb.append('\n');
		sb.append("URL specified : " + url);
		sb.append('\n');
		sb.append("------------------------------------------------------------------------------");
		sb.append('\n');

		return sb.toString();
	}

}
// =============================================================================

