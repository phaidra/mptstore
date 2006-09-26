package org.nsdl.mptstore.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import org.nsdl.mptstore.query.QueryCompiler;
import org.nsdl.mptstore.query.QueryException;
import org.nsdl.mptstore.query.QueryLanguage;
import org.nsdl.mptstore.query.QueryResults;
import org.nsdl.mptstore.query.SPOQueryCompiler;
import org.nsdl.mptstore.query.SQLProvider;
import org.nsdl.mptstore.query.SQLUnionQueryResults;
import org.nsdl.mptstore.rdf.PredicateNode;
import org.nsdl.mptstore.rdf.Triple;

public class GenericDatabaseAdaptor implements DatabaseAdaptor {

    private static final Logger _LOG = Logger.getLogger(GenericDatabaseAdaptor.class.getName());

    private TableManager _tableManager;

    private Map<QueryLanguage, QueryCompiler> _compilerMap;

    /**
     * Get an instance supporting the built-in query languages.
     */
    public GenericDatabaseAdaptor(TableManager tableManager,
                                  boolean backslashIsEscape) {
        _tableManager = tableManager;
        _compilerMap = new HashMap<QueryLanguage, QueryCompiler>();
        _compilerMap.put(QueryLanguage.SPO, 
                         new SPOQueryCompiler(_tableManager,
                                              backslashIsEscape));
    }

    /**
     * Get an instance supporting the specified query languages.
     */
    public GenericDatabaseAdaptor(TableManager tableManager,
                                  Map<QueryLanguage, QueryCompiler> compilerMap) {
        _tableManager = tableManager;
        _compilerMap = compilerMap;
    }

    // Implements DatabaseAdaptor.addTriples(Connection, Iterator<Triple>)
    public void addTriples(Connection conn, 
                           Iterator<Triple> triples) 
            throws ModificationException {
        updateTriples(conn, triples, false);
    }

    // Implements DatabaseAdaptor.deleteTriples(Connection, Iterator<Triple>)
    public void deleteTriples(Connection conn, 
                              Iterator<Triple> triples) 
            throws ModificationException {
        updateTriples(conn, triples, true);
    }

    private void updateTriples(Connection conn,
                               Iterator<Triple> triples,
                               boolean delete)
            throws ModificationException {

        Map<PredicateNode,PreparedStatement> statements = 
                new HashMap<PredicateNode,PreparedStatement>();

        try {
            while (triples.hasNext()) {

                Triple triple = triples.next();
                PredicateNode predicate = triple.getPredicate();

                PreparedStatement statement = statements.get(predicate);
                if (statement == null) {
                    String table = _tableManager.getOrMapTableFor(predicate);
                    String sql;
                    if (delete) {
                        sql = "DELETE FROM " + table + " WHERE s = ? AND o = ?";
                    } else {
                        sql = "INSERT INTO " + table + " (s, o) VALUES (?, ?)";
                    }
                    _LOG.info("Preparing update: " + sql);
                    statement = conn.prepareStatement(sql);
                    statements.put(predicate, statement);
                }

                statement.setString(1, triple.getSubject().toString());
                statement.setString(2, triple.getObject().toString());
                statement.execute();
            }
        } catch (SQLException e) {
            throw new ModificationException("Database update failed", e);
        } finally {

            // close all statements we created for this update
            Iterator<PreparedStatement> iter = statements.values().iterator();
            while (iter.hasNext()) {
                PreparedStatement statement = iter.next();
                try {
                    statement.close();
                } catch (SQLException e) {
                    _LOG.warn("unable to close statement", e);
                }
            }
        }
    }

    // Implements DatabaseAdaptor.deleteAllTriples(Connection)
    public void deleteAllTriples(Connection conn) 
            throws ModificationException {
        try {
            _tableManager.dropAllPredicateTables();
        } catch (SQLException e) {
            throw new ModificationException("Failed to delete all triples", e);
        }
    }


    // Implements DatabaseAdaptor.query(Connection, QueryLanguage, String)
    public QueryResults query(Connection conn, 
                              QueryLanguage lang,
                              int fetchSize,
                              String query) 
            throws QueryException {
        QueryCompiler compiler = _compilerMap.get(lang);
        if (compiler != null) {
            SQLProvider provider = compiler.compile(query);
            return new SQLUnionQueryResults(conn, fetchSize, provider);
        } else {
            throw new QueryException("Query language not supported: " + lang.getName());
        }
    }

}
