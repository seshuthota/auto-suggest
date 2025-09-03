-- Oracle Text setup for PEOPLE(NAME)
-- Run as a user with CTXSYS privileges or via DBA.

BEGIN
  BEGIN
    ctx_ddl.drop_preference('as_lexer');
  EXCEPTION WHEN OTHERS THEN NULL; END;
  BEGIN
    ctx_ddl.drop_preference('as_wl');
  EXCEPTION WHEN OTHERS THEN NULL; END;
END;
/

BEGIN
  -- Lexer: normalize text; base_letter strips diacritics (café ~= cafe)
  ctx_ddl.create_preference('as_lexer', 'BASIC_LEXER');
  ctx_ddl.set_attribute('as_lexer', 'BASE_LETTER', 'YES');

  -- Wordlist: enable prefix and substring indexes for fast 'term%' and '%term%'
  ctx_ddl.create_preference('as_wl', 'BASIC_WORDLIST');
  ctx_ddl.set_attribute('as_wl', 'PREFIX_INDEX', 'YES');
  ctx_ddl.set_attribute('as_wl', 'PREFIX_MIN_LENGTH', '2');
  ctx_ddl.set_attribute('as_wl', 'PREFIX_MAX_LENGTH', '5');
  ctx_ddl.set_attribute('as_wl', 'SUBSTRING_INDEX', 'YES');
END;
/

-- Create CONTEXT index over PEOPLE(NAME)
DECLARE
  v_sql CLOB := 'CREATE INDEX IX_PEOPLE_NAME_CTX ON PEOPLE(NAME)\n' ||
                '  INDEXTYPE IS CTXSYS.CONTEXT\n' ||
                '  PARAMETERS(''LEXER as_lexer WORDLIST as_wl SYNC (ON COMMIT)'')';
BEGIN
  BEGIN
    EXECUTE IMMEDIATE v_sql;
  EXCEPTION WHEN OTHERS THEN
    IF SQLCODE = -955 THEN NULL; ELSE RAISE; END IF; -- ORA-00955: name is already used by an existing object
  END;
END;
/

-- Optional: optimize job (run periodically)
-- EXEC ctx_ddl.optimize_index('IX_PEOPLE_NAME_CTX', 'FULL');

