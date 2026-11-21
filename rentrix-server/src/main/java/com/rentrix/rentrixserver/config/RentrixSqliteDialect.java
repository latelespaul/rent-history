package com.rentrix.rentrixserver.config;

import org.hibernate.community.dialect.SQLiteDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;
import org.hibernate.exception.spi.SQLExceptionConversionDelegate;

/**
 * Hibernate's SQLite dialect plus error mapping: SQLite reports every constraint failure
 * (UNIQUE, CHECK, FOREIGN KEY, NOT NULL) as code 19 with no SQL state, which the stock
 * dialect leaves as a generic JDBC error. Mapping it to {@link ConstraintViolationException}
 * lets services catch {@code DataIntegrityViolationException} (e.g. a duplicate review → 409)
 * the same way on SQLite, MySQL and PostgreSQL.
 */
public class RentrixSqliteDialect extends SQLiteDialect {
	
	static final int SQLITE_BUSY = 5;
	static final int SQLITE_LOCKED = 6;
	static final int SQLITE_CONSTRAINT = 19;
	
	public RentrixSqliteDialect(DialectResolutionInfo info) {
		super(info);
	}
	
	@Override
	public SQLExceptionConversionDelegate buildSQLExceptionConversionDelegate() {
		SQLExceptionConversionDelegate stock = super.buildSQLExceptionConversionDelegate();
		return (sqlException, message, sql) -> switch (sqlException.getErrorCode() & 0xFF) { // primary result code
			case SQLITE_CONSTRAINT -> new ConstraintViolationException(message, sqlException, sql);
			case SQLITE_BUSY, SQLITE_LOCKED -> new LockAcquisitionException(message, sqlException, sql);
			default -> stock == null ? null : stock.convert(sqlException, message, sql);
		};
	}
	
}
