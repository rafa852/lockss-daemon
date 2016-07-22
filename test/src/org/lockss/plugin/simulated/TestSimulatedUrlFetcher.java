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

package org.lockss.plugin.simulated;

import java.io.*;
import org.lockss.test.*;
import org.lockss.config.*;
import org.lockss.plugin.*;

/**
 * This is the test class for org.lockss.plugin.simulated.SimulatedUrlCacher
 *
 * @author  Emil Aalto
 * @version 0.0
 */


public class TestSimulatedUrlFetcher extends LockssTestCase {
  private MockLockssDaemon theDaemon;
  private SimulatedArchivalUnit sau;
  private String tempDirPath;

  public void setUp() throws Exception {
    super.setUp();
    tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    ConfigurationUtil.setFromArgs(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST,
				  tempDirPath);

    theDaemon = getMockLockssDaemon();
    theDaemon.getPluginManager();
    Plugin simPlugin = PluginTestUtil.findPlugin(SimulatedPlugin.class);

    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    sau = new SimulatedArchivalUnit(simPlugin);
    Configuration auConfig = ConfigurationUtil.fromArgs("root", tempDirPath);
    sau.setConfiguration(auConfig);
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  public static void main(String[] argv) {
    String[] testCaseList = {TestSimulatedUrlFetcher.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

}
