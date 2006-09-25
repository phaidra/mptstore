package org.nsdl.mptstore.query;

import java.text.ParseException;

import java.util.ArrayList;
import java.util.List;

import org.nsdl.mptstore.core.TableManager;
import org.nsdl.mptstore.rdf.NTParser;
import org.nsdl.mptstore.rdf.Triple;
import org.nsdl.mptstore.rdf.URIReference;

/**
 * Compiler for SPO queries.
 *
 * SPO is a simple query language that returns triples (subject, predicate,
 * object) given a single triple pattern where an asterisk in any
 * position means "any".
 *
 * <p>
 *   The grammar for SPO queries is the same as the N-Triples "triple"
 *   production, except it allows an asterisk in any node position and 
 *   is not terminated with a dot.
 * </p>
 */
public class SPOQueryCompiler implements QueryCompiler {

    private TableManager _tableManager;
    private boolean _backslashIsEscape;

    public SPOQueryCompiler(TableManager tableManager,
                            boolean backslashIsEscape) {
        _tableManager = tableManager;
        _backslashIsEscape = backslashIsEscape;
    }

    // Implements QueryCompiler.compile(String)
    public SQLProvider compile(String query) 
            throws QueryException {
        try {
            return new SPOSQLProvider(_tableManager,
                                      _backslashIsEscape,
                                      NTParser.parseTriplePattern(query));
        } catch (ParseException e) {
            throw new QuerySyntaxException("Error parsing SPO query", e);
        }
    }

}
