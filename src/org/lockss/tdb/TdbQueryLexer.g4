/*
 * $Id: TdbQueryLexer.g4,v 1.3 2014-09-03 22:07:48 thib_gc Exp $
 */

/*

Copyright (c) 2000-2014 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

lexer grammar TdbQueryLexer;

@lexer::header {

/* ===================================================================

WARNING - WARNING - WARNING - WARNING - WARNING - WARNING - WARNING

If you are modifying a Java file with this warning in it, you are
making changes in the wrong place. This Java file was generated by
ANTLR from a grammar file. You should be editing the grammar file
instead.

=================================================================== */

package org.lockss.tdb;

}

@lexer::members {

  void processString() {
    String txt = getText();
    if (txt.indexOf('\\') < 0) {
      setText(txt.substring(1, txt.length() - 1));
      return;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 1 ; i < txt.length() - 1 ; ++i) {
      char ch = txt.charAt(i);
      if (ch != '\\') {
        sb.append(ch);
      }
    }
    setText(sb.toString());
  }

}

AND            : 'and' ;
OR             : 'or' ;
IS             : 'is' ;
NOT            : 'not' ;
SET            : 'set' ;
EQUALS         : '=' ;
NOT_EQUALS     : '!=' ;
MATCHES        : '~' ;
DOES_NOT_MATCH : '!~' ;
PAREN_OPEN     : '(' ;
PAREN_CLOSE    : ')' ;
STRING         : '"' ( '\\' [\\"] | ~[\\"] )* '"' {processString();}
               | '\'' ( '\\' [\\'] | ~[\\'] )* '\'' {processString();}
               ;
IDENTIFIER     : ( ( 'au' | 'title' | 'publisher' ) ':' )? [a-zA-Z] [a-zA-Z0-9_.]* ( '[' [a-zA-Z0-9] [a-zA-Z0-9_./]* ']' )? ;
WHITESPACE     : [ \t\r\n]+ -> skip ;
