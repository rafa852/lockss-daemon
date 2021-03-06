/*
 * $Id$
 */

/*

Copyright (c) 2000-2009 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire;

import java.io.*;

import org.lockss.filter.pdf.*;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.highwire.HighWirePdfFilterFactory.*;
import org.lockss.util.*;
import org.pdfbox.util.operator.OperatorProcessor;

@Deprecated
public class AmericanMedicalAssociationPdfTransform
    implements OutputDocumentTransform,
               ArchivalUnitDependent {

  public static class NormalizeCurrentAsOf extends PageStreamTransform {
    
    public static class ReplaceCurrentAsOf extends ReplaceString {

      @Override
      public String getReplacement(String match) {
        return "current as of ";
      }

      @Override
      public boolean identify(String candidate) {
        return candidate.startsWith("current as of ");
      }
      
    }
    
    
    public NormalizeCurrentAsOf(final ArchivalUnit au) throws IOException {
      super(new OperatorProcessorFactory() {
              public OperatorProcessor newInstanceForName(String className) throws LinkageError, ExceptionInInitializerError, ClassNotFoundException, IllegalAccessException, InstantiationException, SecurityException {
                return (OperatorProcessor)au.getPlugin().newAuxClass(className,
                                                                     OperatorProcessor.class);
              }
            },
            // "Tj" operator: replace string conditionally using ReplaceCurrentAsOf
            PdfUtil.SHOW_TEXT, ReplaceCurrentAsOf.class);
    }
    
  }

  @Deprecated
  public static class Simplified
      extends ResilientTextScrapingDocumentTransform
      implements ArchivalUnitDependent {

    protected ArchivalUnit au;

    public void setArchivalUnit(ArchivalUnit au) {
      this.au = au;
    }

    public DocumentTransform makePreliminaryTransform() throws IOException {
      if (au == null) throw new IOException("Uninitialized AU-dependent transform");
      return new ConditionalDocumentTransform(// If on the first page...
                                              new TransformFirstPage(// ...collapsing "Downloaded from" succeeds,
                                                                     new CollapseDownloadedFrom(au)),
                                              // Then on all other pages...
                                              new TransformEachPageExceptFirst(// ...collapse "Downloaded from",
                                                                               new CollapseDownloadedFrom(au)),
                                              // And on the first page...
                                              new TransformFirstPage(// normalize "current as of"
                                                                     new NormalizeCurrentAsOf(au)));
    }

  }

  protected ArchivalUnit au;

  public void setArchivalUnit(ArchivalUnit au) {
    this.au = au;
  }

  public boolean transform(PdfDocument pdfDocument,
                           OutputStream outputStream) {
    return PdfUtil.applyAndSave(this,
                                pdfDocument,
                                outputStream);
  }

  public boolean transform(PdfDocument pdfDocument) throws IOException {
    if (au == null) throw new IOException("Uninitialized AU-dependent transform");
    DocumentTransform documentTransform = new ConditionalDocumentTransform(// If on the first page...
                                                                           new TransformFirstPage(// ...collapsing "Downloaded from" and normalizing the hyperlinks succeeds,
                                                                                                  new CollapseDownloadedFromAndNormalizeHyperlinks(au)),
                                                                           // Then on all other pages...
                                                                           new TransformEachPageExceptFirst(// ...collapse "Downloaded from" and normalize the hyperlink,
                                                                                                            new CollapseDownloadedFromAndNormalizeHyperlinks(au)),
                                                                           // And on the first page...
                                                                           new TransformFirstPage(// ...normalize "current as of",
                                                                                                  new NormalizeCurrentAsOf(au)),
                                                                           // ...and normalize the metadata
                                                                           new NormalizeMetadata());
    return documentTransform.transform(pdfDocument);
  }

}
