/*
 * $Id: MockCachedUrlSetSpec.java,v 1.7 2003-02-20 02:27:37 aalto Exp $
 */

/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.test;

import java.util.*;
import org.lockss.daemon.*;
import org.lockss.util.*;

/**
 * This is a mock version of <code>CachedUrlSetSpec</code> used for testing
 *
 * @author  Thomas S. Robertson
 * @version 0.0
 */

public class MockCachedUrlSetSpec implements CachedUrlSetSpec {
  private String root = null;
  private String regExp = null;

  public MockCachedUrlSetSpec() {
  }

  public MockCachedUrlSetSpec(String root, String regExp) {
    this.root = root;
    this.regExp = regExp;
  }

  public String getUrl() {
    return root;
  }

  public boolean equals(Object obj) {
    if (obj instanceof MockCachedUrlSetSpec) {
      MockCachedUrlSetSpec spec = (MockCachedUrlSetSpec)obj;
      return ((root.equals(spec.root)) &&
              (regExp.equals(spec.regExp)));
    } else {
      return false;
    }
  }

  public boolean matches(String url) {
    return url.startsWith(root);
  }
}
