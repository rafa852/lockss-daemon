/*
 * $Id: TestV3Poller.java,v 1.1.2.11 2004-10-13 01:30:21 dshr Exp $
 */

/*

Copyright (c) 2004 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.poller;

import java.io.*;
import java.util.*;
import java.net.*;
import org.lockss.app.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.protocol.*;
import org.lockss.util.*;
import org.lockss.test.*;
import org.lockss.config.*;
import org.lockss.effort.*;
import org.lockss.repository.LockssRepositoryImpl;

/** JUnitTest case for class: org.lockss.poller.V3Poller */
public class TestV3Poller extends LockssTestCase {
  private static Logger log = Logger.getLogger("TestV3Poll");
  private static String[] rootV3urls = {
    "http://www.test.net",
    "http://www.test1.net", "http://www.test2.net"};
  private static String lwrbnd = "test1.doc";
  private static String uprbnd = "test3.doc";
  private static long testduration = Constants.DAY;

  protected MockArchivalUnit testau;
  private IdentityManager idmgr;
  private MockLockssDaemon theDaemon;

  protected PeerIdentity[] testID = new PeerIdentity[10];
  protected int[] msgType = {
    V3LcapMessage.MSG_POLL,
    V3LcapMessage.MSG_POLL_ACK,
    V3LcapMessage.MSG_POLL_PROOF,
    V3LcapMessage.MSG_VOTE,
    V3LcapMessage.MSG_REPAIR_REQ,
    V3LcapMessage.MSG_REPAIR_REP,
    V3LcapMessage.MSG_EVALUATION_RECEIPT,
  };
  protected LcapMessage[] testV3msg = new LcapMessage[msgType.length];;
  protected V3Poll[] testV3polls;
  protected PollSpec[] testSpec = new PollSpec[msgType.length];
  protected PollManager pollmanager;

  protected void setUp() throws Exception {
    super.setUp();
    TimeBase.setSimulated();

    initRequiredServices();

    testau.setPlugin(new MyMockPlugin());

    initTestPeerIDs();
    initTestPolls();
    initTestMsg();
  }

  /** tearDown method for test case
   * @throws Exception if removePoll failed
   */
  public void tearDown() throws Exception {
    pollmanager.stopService();
    theDaemon.getLockssRepository(testau).stopService();
    theDaemon.getHashService().stopService();
    theDaemon.getStreamRouterManager().stopService();
    theDaemon.getSystemMetrics().stopService();
    TimeBase.setReal();
    for(int i=0; i<testV3msg.length; i++) {
      if (testV3msg[i] != null)
	pollmanager.removePoll(testV3msg[i].getKey());
    }
    theDaemon.getPollManager().stopService();
    theDaemon.getPluginManager().stopService();
    super.tearDown();
  }

  // Tests

  public void testInitialVoterState() {
    assertTrue(testV3polls[0] instanceof V3Poller);
    assertEquals("Poll " + testV3polls[0] + " should be in Initializing",
		 V3Poller.STATE_INITIALIZING,
		 testV3polls[0].getPollState());
  }

  public void testVoterDuration() {
    V3Poller poll = (V3Poller) testV3polls[0];
    long duration = poll.getDeadline().getRemainingTime();
    final int numSteps = 10;
    long step = (duration - 1) / numSteps;
    String key = poll.getKey();
    assertTrue("Duration " + duration + " means step " + step, step > 1);
    assertEquals("Poll " + poll + " should be in Initializing",
		 V3Poller.STATE_INITIALIZING,
		 poll.getPollState());
    for (int i = 0; i < numSteps; i++) {
      assertTrue("Poll " + poll + " should be active",
		 pollmanager.isPollActive(key));
      assertFalse("Poll " + poll + " should not be closed",
		  pollmanager.isPollClosed(key));
      assertFalse("Poll " + poll + " should not be suspended",
		  pollmanager.isPollSuspended(key));
      TimeBase.step(step);
    }
    assertTrue("Poll " + poll + " should be active",
	       pollmanager.isPollActive(key));
    assertFalse("Poll " + poll + " should not be closed",
		pollmanager.isPollClosed(key));
    assertFalse("Poll " + poll + " should not be suspended",
		pollmanager.isPollSuspended(key));
    TimeBase.step(step);
    assertFalse("Poll " + poll + " should not be active",
		pollmanager.isPollActive(key));
    assertTrue("Poll " + poll + " should be closed",
	       pollmanager.isPollClosed(key));
    assertFalse("Poll " + poll + " should not be suspended",
		pollmanager.isPollSuspended(key));
    PollTally tally = poll.getVoteTally();
    assertNotNull(tally);
    List votes = tally.getPollVotes();
    assertTrue(votes.isEmpty());
  }

  public void testNormalVoterStateTransitions() {
    //  Set up effort service stuff
    MockEffortService es = (MockEffortService)theDaemon.getEffortService();
    es.setGenerateProofResult(true);
    es.setVerifyProofResult(true);
    es.setProofDuration(400);
    es.setProofException(null);
    es.setGenerateVoteResult(true);
    es.setVerifyVoteResult(true);
    es.setAgreeVoteResult(true);
    es.setVoteDuration(400);
    es.setVoteException(null);
    //  Check starting conditions
    V3Poller poll = (V3Poller)testV3polls[0];
    String key = poll.getKey();
    assertEquals("Poll " + poll + " should be in SendingPoll",
		 V3Poller.STATE_INITIALIZING,
		 poll.getPollState());
    assertTrue("Poll " + poll + " should be active",
	       pollmanager.isPollActive(key));
    assertFalse("Poll " + poll + " should not be closed",
		pollmanager.isPollClosed(key));
    assertFalse("Poll " + poll + " should not be suspended",
		pollmanager.isPollSuspended(key));
    List peers = new ArrayList(1);
    peers.add(testID[1]);
    doPollsWithPeers(testV3msg, (V3Poller) testV3polls[0], peers, null, null);
    assertEquals("Poll " + poll + " should be in Finalizing",
		 V3Poller.STATE_FINALIZING,
		 poll.getPollState());
    assertFalse("Poll " + poll + " should not be active",
	       pollmanager.isPollActive(key));
    assertTrue("Poll " + poll + " should not be closed",
		pollmanager.isPollClosed(key));
    assertFalse("Poll " + poll + " should not be suspended",
		pollmanager.isPollSuspended(key));
  }

  public void testPollWithNineAgreeVoters() {
    //  Set up effort service stuff
    MockEffortService es = (MockEffortService)theDaemon.getEffortService();
    es.setGenerateProofResult(true);
    es.setVerifyProofResult(true);
    es.setProofDuration(400);
    es.setProofException(null);
    es.setGenerateVoteResult(true);
    es.setVerifyVoteResult(true);
    es.setAgreeVoteResult(true);
    es.setVoteDuration(400);
    es.setVoteException(null);
    //  Check starting conditions
    V3Poller poll = (V3Poller)testV3polls[0];
    String key = poll.getKey();
    assertEquals("Poll " + poll + " should be in SendingPoll",
		 V3Poller.STATE_INITIALIZING,
		 poll.getPollState());
    assertTrue("Poll " + poll + " should be active",
	       pollmanager.isPollActive(key));
    assertFalse("Poll " + poll + " should not be closed",
		pollmanager.isPollClosed(key));
    assertFalse("Poll " + poll + " should not be suspended",
		pollmanager.isPollSuspended(key));
    List peers = new ArrayList(testID.length);
    for (int i = 1; i < testID.length; i++) {
      peers.add(testID[i]);
    }
    doPollsWithPeers(testV3msg, (V3Poller) testV3polls[0], peers, null, null);
    assertEquals("Poll " + poll + " should be in Finalizing",
		 V3Poller.STATE_FINALIZING,
		 poll.getPollState());
    assertFalse("Poll " + poll + " should not be active",
	       pollmanager.isPollActive(key));
    assertTrue("Poll " + poll + " should not be closed",
		pollmanager.isPollClosed(key));
    assertFalse("Poll " + poll + " should not be suspended",
		pollmanager.isPollSuspended(key));
  }

  public void testPollWithThreeAgreeThreeDisagreeThreeInvalidVoters() {
    //  Set up effort service stuff
    MockEffortService es = (MockEffortService)theDaemon.getEffortService();
    es.setGenerateProofResult(true);
    es.setVerifyProofResult(true);
    es.setProofDuration(400);
    es.setProofException(null);
    es.setGenerateVoteResult(true);
    es.setVerifyVoteResult(true);
    es.setAgreeVoteResult(true);
    es.setVoteDuration(400);
    es.setVoteException(null);
    //  Check starting conditions
    V3Poller poll = (V3Poller)testV3polls[0];
    String key = poll.getKey();
    assertEquals("Poll " + poll + " should be in SendingPoll",
		 V3Poller.STATE_INITIALIZING,
		 poll.getPollState());
    assertTrue("Poll " + poll + " should be active",
	       pollmanager.isPollActive(key));
    assertFalse("Poll " + poll + " should not be closed",
		pollmanager.isPollClosed(key));
    assertFalse("Poll " + poll + " should not be suspended",
		pollmanager.isPollSuspended(key));
    int num = testID.length/3;
    List agree = new ArrayList(num);
    List disagree = new ArrayList(num);
    List invalid = new ArrayList(num);
    for (int i = 0; i < num; i++) {
      agree.add(testID[i+1]);
      disagree.add(testID[i+num+1]);
      invalid.add(testID[i+num+num+1]);
    }
    doPollsWithPeers(testV3msg, (V3Poller) testV3polls[0],
		     agree, disagree, invalid);
    assertEquals("Poll " + poll + " should be in Finalizing",
		 V3Poller.STATE_FINALIZING,
		 poll.getPollState());
    assertFalse("Poll " + poll + " should not be active",
	       pollmanager.isPollActive(key));
    assertTrue("Poll " + poll + " should not be closed",
		pollmanager.isPollClosed(key));
    assertFalse("Poll " + poll + " should not be suspended",
		pollmanager.isPollSuspended(key));
  }

  //  Support methods

  private void doPollsWithPeers(LcapMessage[] msgs, V3Poller poll,
				List agree, List disagree, List invalid) {
    String key = poll.getKey();
    List peers = new ArrayList();
    int numAgree = 0;
    int numDisagree = 0;
    int numInvalid = 0;
    MockEffortService es = (MockEffortService)theDaemon.getEffortService();
    if (invalid != null) {
      peers.addAll(invalid);
      numInvalid = invalid.size();
    }
    if (disagree != null) {
      peers.addAll(disagree);
      numDisagree = disagree.size();
    }
    if (agree != null) {
      peers.addAll(agree);
      numAgree = agree.size();
    }
    int totalPeers = peers.size();
    for (int i = totalPeers; i > 0; i--) {

      // Decide to solicit a vote from testID[1],  go to SendingPoll
      poll.solicitVotesFrom(peers);
      assertEquals("Poll " + poll + " vote " + i + " should be in SendingPoll",
		   V3Poller.STATE_SENDING_POLL,
		   poll.getPollState());
      assertTrue("Poll " + poll + " vote " + i + " should be active",
		 pollmanager.isPollActive(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be closed",
		  pollmanager.isPollClosed(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be suspended",
		  pollmanager.isPollSuspended(key));
      stepTimeUntilPollStateChanges(poll);
      // And after a while go to WaitingPollAck
      assertEquals("Poll " + poll + " vote " + i + " should be in WaitingPollAck",
		   V3Poller.STATE_WAITING_POLL_ACK,
		   poll.getPollState());
      assertTrue("Poll " + poll + " vote " + i + " should be active",
		 pollmanager.isPollActive(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be closed",
		  pollmanager.isPollClosed(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be suspended",
		  pollmanager.isPollSuspended(key));
      TimeBase.step(500);
      //  Receive a PollAck message, go to SendingPollProof
      try {
	pollmanager.handleIncomingMessage(testV3msg[1]);
      } catch (IOException ex) {
	fail("Message " + testV3msg[1].toString() + " threw " + ex);
      }
      assertEquals("Poll " + poll + " vote " + i + " should be in SendingPollProof",
		   V3Poller.STATE_SENDING_POLL_PROOF,
		   poll.getPollState());
      assertTrue("Poll " + poll + " vote " + i + " should be active",
		 pollmanager.isPollActive(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be closed",
		  pollmanager.isPollClosed(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be suspended",
		  pollmanager.isPollSuspended(key));
      stepTimeUntilPollStateChanges(poll);
      //  Eventually go to WaitingVote
      assertEquals("Poll " + poll + " vote " + i + " should be in WaitingPollProof",
		   V3Poller.STATE_WAITING_VOTE,
		   poll.getPollState());
      assertTrue("Poll " + poll + " vote " + i + " should be active",
		 pollmanager.isPollActive(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be closed",
		  pollmanager.isPollClosed(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be suspended",
		  pollmanager.isPollSuspended(key));
      TimeBase.step(500);
      //  Receive a Vote message, go to SendingPoll for next voter
      //  unless no more voters in which case go to SendingRepairRequest
      try {
	pollmanager.handleIncomingMessage(testV3msg[3]);
      } catch (IOException ex) {
	fail("Message " + testV3msg[3].toString() + " threw " + ex);
      }
      assertTrue("Poll " + poll + " vote " + i + " should be active",
		 pollmanager.isPollActive(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be closed",
		  pollmanager.isPollClosed(key));
      assertFalse("Poll " + poll + " vote " + i + " should not be suspended",
		  pollmanager.isPollSuspended(key));
      if (i > 1) {
	assertEquals("Poll " + poll + " vote " + i + " should be in SendingPoll",
		     V3Poller.STATE_SENDING_POLL,
		     poll.getPollState());
      } else {
	assertEquals("Poll " + poll + " vote " + i + " should be in SendingRepairReq",
		     V3Poller.STATE_SENDING_REPAIR_REQ,
		     poll.getPollState());
      }
      assertTrue(peers.size() == i-1);
    }
    for (int i = totalPeers; i > 0; i--) {
      if ((totalPeers - i) < numInvalid) {
	es.setVerifyVoteResult(false);
      } else {
	es.setVerifyVoteResult(true);
      }
      if ((totalPeers - i) < (numInvalid + numDisagree)) {
	es.setAgreeVoteResult(false);
      } else {
	es.setAgreeVoteResult(true);
      }
      TimeBase.step(500);
      //  Verify the poll effort
      TimeBase.step(500);
      //  Verify the vote
      if ((totalPeers - i) < numInvalid) {
	assertEquals("Poll " + poll + " vote " + i + " should be in state SenndingRepairReq",
		     V3Poller.STATE_SENDING_REPAIR_REQ,
		     poll.getPollState());
	log.debug("XXX before invalid step 500");
	TimeBase.step(500);
      } else {
	assertEquals("Poll " + poll + " vote " + i + " should be in SendingRepairReq",
		     V3Poller.STATE_SENDING_REPAIR_REQ,
		     poll.getPollState());
	assertTrue("Poll " + poll + " vote " + i + " should be active",
		   pollmanager.isPollActive(key));
	assertFalse("Poll " + poll + " vote " + i + " should not be closed",
		    pollmanager.isPollClosed(key));
	assertFalse("Poll " + poll + " vote " + i + " should not be suspended",
		    pollmanager.isPollSuspended(key));
	log.debug("XXX before third step 500");
	stepTimeUntilPollStateChanges(poll);
	assertEquals("Poll " + poll + " vote " + i + " should be in SendingReceipt",
		     V3Poller.STATE_SENDING_RECEIPT,
		     poll.getPollState());
	assertTrue("Poll " + poll + " vote " + i + " should not be active",
		   pollmanager.isPollActive(key));
	assertFalse("Poll " + poll + " vote " + i + " should be closed",
		    pollmanager.isPollClosed(key));
	assertFalse("Poll " + poll + " vote " + i + " should not be suspended",
		    pollmanager.isPollSuspended(key));
	log.debug("XXX before fourth step 500");
	stepTimeUntilPollStateChanges(poll);
	if (i > 1) {
	  assertEquals("Poll " + poll + " vote " + i + " should be in SendingRepairReq",
		       V3Poller.STATE_SENDING_REPAIR_REQ,
		       poll.getPollState());
	  assertTrue("Poll " + poll + " vote " + i + " should be active",
		     pollmanager.isPollActive(key));
	  assertFalse("Poll " + poll + " vote " + i + " should not be closed",
		      pollmanager.isPollClosed(key));
	  assertFalse("Poll " + poll + " vote " + i + " should not be suspended",
		      pollmanager.isPollSuspended(key));
	  log.debug("XXX before fifth step 500");
	}
      }
    }
  }

  private void stepTimeUntilPollStateChanges(V3Poller poll) {
    // XXX
    int oldState = poll.getPollState();
    int newState = -1;
    long startTime = TimeBase.nowMs();
    do {
      TimeBase.step(100);
    } while ((newState = poll.getPollState()) == oldState);
    log.debug("Change from " + V3Poller.getPollStateName(oldState) +
	      " to " + V3Poller.getPollStateName(newState) +
	      " after " + (TimeBase.nowMs() - startTime) + "ms");
  }

  private void initRequiredServices() {
    theDaemon = new MockLockssDaemon();
    pollmanager = theDaemon.getPollManager();
    theDaemon.setEffortService((EffortService) new MockEffortService());

    theDaemon.getPluginManager();
    testau = PollTestPlugin.PTArchivalUnit.createFromListOfRootUrls(rootV3urls);
    PluginUtil.registerArchivalUnit(testau);

    String tempDirPath = null;
    try {
      tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    }
    catch (IOException ex) {
      fail("unable to create a temporary directory");
    }

    Properties p = new Properties();
    p.setProperty(IdentityManager.PARAM_IDDB_DIR, tempDirPath + "iddb");
    p.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    p.setProperty(IdentityManager.PARAM_LOCAL_IP, "127.0.0.1");
    p.setProperty(IdentityManager.PARAM_LOCAL_V3_PORT,  "2048");
    p.setProperty(ConfigManager.PARAM_NEW_SCHEDULER, "false");
    ConfigurationUtil.setCurrentConfigFromProps(p);
    idmgr = theDaemon.getIdentityManager();
    idmgr.startService();
    //theDaemon.getSchedService().startService();
    theDaemon.getHashService().startService();
    theDaemon.getStreamRouterManager().startService();
    theDaemon.getSystemMetrics().startService();
    theDaemon.getEffortService().startService();
    theDaemon.getActivityRegulator(testau).startService();
    theDaemon.setNodeManager(new MockNodeManager(), testau);
    pollmanager.startService();
  }

  private void initTestPeerIDs() {
    try{
      testID[0] = idmgr.stringToPeerIdentity("127.0.0.1");
      testID[1] = idmgr.stringToPeerIdentity("1.1.1.1");
      testID[2] = idmgr.stringToPeerIdentity("2.1.1.1");
      testID[3] = idmgr.stringToPeerIdentity("3.1.1.1");
      testID[4] = idmgr.stringToPeerIdentity("4.1.1.1");
      testID[5] = idmgr.stringToPeerIdentity("5.1.1.1");
      testID[6] = idmgr.stringToPeerIdentity("6.1.1.1");
      testID[7] = idmgr.stringToPeerIdentity("7.1.1.1");
      testID[8] = idmgr.stringToPeerIdentity("8.1.1.1");
      testID[9] = idmgr.stringToPeerIdentity("9.1.1.1");
    } catch (IdentityManager.MalformedIdentityKeyException ex) {
      fail("can't open test host:" + ex);
    }
    assertTrue(testID[0].isLocalIdentity());
    for (int i = 1; i < testID.length; i++) {
      assertFalse("ID " + i + " should not be local",
		  testID[i].isLocalIdentity());
    }

  }

  private void initTestMsg() throws Exception {
    PollFactory ppf = pollmanager.getPollFactory(Poll.V3_POLL);
    assertNotNull("PollFactory should not be null", ppf);
    assertTrue(ppf instanceof V3PollFactory);
    V3PollFactory pf = (V3PollFactory)ppf;
    PollSpec spec = testSpec[0];
    ((MockCachedUrlSet)spec.getCachedUrlSet()).setHasContent(false);
    long duration = pf.calcDuration(Poll.CONTENT_POLL,
				    spec.getCachedUrlSet(),
				    pollmanager);
    log.debug("Duration is " + duration);
    byte[] challenge = testV3polls[0].getChallenge();

    for (int i= 0; i<testV3msg.length; i++) {
      testSpec[i] = spec;
      testV3msg[i] =
	V3LcapMessage.makeRequestMsg(spec,
				     null, // XXX entries not needed
				     challenge,
				     pollmanager.makeVerifier(duration),
				     msgType[i],
				     duration,
				     testID[1]);
      assertNotNull(testV3msg[i]);
      log.debug("Made " + testV3msg[i] + " from " + spec);
    }

  }

  private void initTestPolls() throws Exception {
    PollSpec spec = new MockPollSpec(testau, rootV3urls[0],
				     lwrbnd, uprbnd, Poll.CONTENT_POLL,
				     Poll.V3_POLL);
    assertEquals(spec.getPollType(), Poll.CONTENT_POLL);
    assertEquals(spec.getPollVersion(), Poll.V3_POLL);
    for (int i= 0; i<testV3msg.length; i++) {
      testSpec[i] = spec;
    }
    testV3polls = new V3Poll[1];
    for (int i = 0; i < testV3polls.length; i++) {
      log.debug3("initTestPolls: V3 " + i);
      BasePoll p = pollmanager.callPoll(testSpec[i]);
      assertNotNull(p);
      log.debug("initTestPolls: V3 " + i + " returns " + p);
      switch (i) {
      case 0:
	assertTrue(p instanceof V3Poller);
	break;
      default:
	assertNull(p);
	break;
      }
      testV3polls[i] = (V3Poll)p;
      log.debug3("initTestPolls: " + i + " " + p.toString());
    }
  }

  public class MyMockPlugin extends MockPlugin {
    public CachedUrlSet makeCachedUrlSet(ArchivalUnit owner,
					 CachedUrlSetSpec cuss) {
      return new PollTestPlugin.PTCachedUrlSet((MockArchivalUnit)owner, cuss);
    }
  }

  /** Executes the test case
   * @param argv array of Strings containing command line arguments
   * */
  public static void main(String[] argv) {
    String[] testCaseList = {
      TestV3Poll.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }
}
