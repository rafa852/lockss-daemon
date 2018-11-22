/*
 * $Id$
 */

/*

Copyright (c) 2000-2016 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.pdf.pdfbox;

import java.io.IOException;
import java.util.*;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.lockss.pdf.*;

/**
 * <p>
 * A {@link PdfTokenStream} implementation based on PDFBox 1.6.0.
 * </p>
 * <p>
 * This class acts as an adapter for the {@link PDStream} class, but
 * the origin of the wrapped instance comes from {@link #getPdStream()}
 * </p>
 * 
 * @author Thib Guicherd-Callin
 * @since 1.56
 * @see PdfBoxDocumentFactory
 */
public abstract class PdfBoxTokenStream implements PdfTokenStream {

  /**
   * <p>
   * The parent {@link PdfBoxPage} instance.
   * </p>
   * 
   * @since 1.56
   */
  protected PdfBoxPage pdfBoxPage;
  
  /**
   * <p>
   * Constructor.
   * </p>
   * 
   * @param pdfBoxPage The parent {@link PdfBoxPage} instance.
   * @since 1.56
   */
  public PdfBoxTokenStream(PdfBoxPage pdfBoxPage) {
    this.pdfBoxPage = pdfBoxPage;
  }
  
  @Override
  public PdfBoxPage getPage() {
    return pdfBoxPage;
  }
  
  @Override
  public List<PdfToken> getTokens() throws PdfException {
    List<PdfToken> tokens = new ArrayList<PdfToken>();
    PDStream pdStream = getPdStream();
    if (pdStream == null) {
      return tokens; // Blank page with null stream
    }
    PDFStreamParser pdfStreamParser = null;
    try {
      pdfStreamParser = new PDFStreamParser(pdStream);
      for (Object tok = pdfStreamParser.parseNextToken() ; tok != null ; tok = pdfStreamParser.parseNextToken()) {
        tokens.add(PdfBoxTokens.convertOne(tok));
      }
      decodeStringsWithFontContext(tokens);
      return tokens;
    }
    catch (IOException ioe) {
      throw new PdfException(ioe);
    }
  }

  /**
   * <p>
   * Decodes strings using font contexts, in place in the given list.
   * </p>
   * 
   * @param tokens
   *          A list of tokens.
   * @param currentFont
   *          The current font in the surrounding context of the list of tokens.
   * @throws PdfException
   *           if a referenced font cannot be found.
   * @since 1.62
   */
  protected void decodeStringsWithFontContext(List<PdfToken> tokens) throws PdfException {
    decodeStringsWithFontContext(tokens, null);
  }

  /**
   * <p>
   * Decodes strings using font contexts, in place in the given list, using a
   * current font at the beginning.
   * </p>
   * 
   * @param tokens
   *          A list of tokens.
   * @param currentFont
   *          The current font in the surrounding context of the list of tokens.
   * @throws PdfException
   *           if a referenced font cannot be found.
   * @since 1.62
   */
  protected void decodeStringsWithFontContext(List<PdfToken> tokens,
                                              PDFont currentFont)
      throws PdfException {
    PdfTokenFactory factory = PdfUtil.getTokenFactory(this);
    for (int i = 0 ; i < tokens.size() ; ++i) {
      PdfToken token = tokens.get(i);
      if (token.isOperator() && PdfOpcodes.SET_TEXT_FONT.equals(token.getOperator())) {
        if (i == 0 || i == 1) {
          continue; // Malformed; ignore
        }
        PdfToken fontName = tokens.get(i - 2);
        PdfToken fontSize = tokens.get(i - 1);
        if (!fontName.isName() || !(fontSize.isFloat() || fontSize.isInteger())) {
          continue; // Malformed; ignore
        }
        PDResources streamResources = getStreamResources();
        if (streamResources == null) {
          throw new PdfException("Current context has no PDResources instance");
        }
//PB2         currentFont = (PDFont)streamResources.getFonts().get(fontName.getName());
        try {
          currentFont = (PDFont)streamResources.getFont(COSName.getPDFName(fontName.getName()));
        } catch (IOException ioe) {
          throw new PdfException(String.format("Font '%s' not found", fontName.getName()));              
        }
        if (currentFont == null) {
          throw new PdfException(String.format("Font '%s' not found", fontName.getName()));              
        }
      }
      else if (token.isString()) {
        if (currentFont == null) {
          throw new PdfException(String.format("No font set at index %d", i));              
        }
        // See PDFBox 1.8.2, PDFStreamEngine, lines 387-514
//SB2         StringBuilder sb = new StringBuilder();
//SB2         byte[] bytes = PdfBoxTokens.asCOSString(token).getBytes();
//SB2         int codeLength = 1;
//SB2         for (int j = 0; j < bytes.length; j += codeLength) {
//SB2           codeLength = 1;
//SB2           String str;
//SB2           try {
//SB2             str = currentFont.encode(bytes, j, codeLength);
//SB2             if (str == null && j + 1 < bytes.length) {
//SB2               codeLength++;
//SB2               str = currentFont.encode(bytes, j, codeLength);
//SB2             }
//SB2           } catch (IOException ioe) {
//SB2             throw new PdfException(String.format("Error decoding string at index %d", i), ioe);
//SB2           }
//SB2           if (str != null) {
//SB2             sb.append(str);
//SB2           }
//SB2         }
//SB2         tokens.set(i, factory.makeString(sb.toString()));
        try {
          tokens.set(i, factory.makeString(new String(currentFont.encode(token.getString()))));
        } catch (IOException ioe) {
          throw new PdfException(String.format("Token '%s' cannot be encoded", token));              
        }
      }
      else if (token.isArray()) {
        List<PdfToken> array = token.getArray();
        try {
          decodeStringsWithFontContext(array, currentFont);
        } catch (PdfException pdfe) {
          throw new PdfException(String.format("Error processing list at index %d", i), pdfe);
        }
        tokens.set(i, factory.makeArray(array));
      }
    }
    
  }
  
  /**
   * <p>
   * Retrieves the {@link PDStream} instance underpinning this PDF token stream.
   * </p>
   * 
   * @return The {@link PDStream} instance this instance represents.
   * @since 1.56
   */
  protected abstract PDStream getPdStream();

  /**
   * <p>
   * Retrieves a {@link PDResources} instance suitable for the context of this
   * token stream.
   * </p>
   * 
   * @return The {@link PDResources} instance for this token stream.
   * @since 1.64
   */
  protected abstract PDResources getStreamResources();
  
  /**
   * <p>
   * Convenience method to create a new {@link PDStream} instance.
   * </p>
   * 
   * @return A new {@link PDStream} instance based on this document.
   * @since 1.56
   */
  protected PDStream makeNewPdStream() {
    return new PDStream(pdfBoxPage.pdfBoxDocument.pdDocument);
  }
  
}
