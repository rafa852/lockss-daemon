/*
 * $Id: ReputationTransfers.java,v 1.5 2012-09-24 18:51:29 barry409 Exp $
 */

/*

Copyright (c) 2012 Board of Trustees of Leland Stanford Jr. University,
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

import java.util.*;

import org.lockss.config.Configuration;
import org.lockss.config.ConfigManager;
import org.lockss.config.CurrentConfig;
import org.lockss.protocol.IdentityManager;
import org.lockss.protocol.PeerIdentity;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;


/**
 * Extend reputation from old PID to new PID.  Reputation may be
 * extended from and to only one peer.  E.g., both {A->B, A->C} and
 * {B->A, C->A} are illegal; a warning will be logged, results are
 * unspecified.  Transitive mappings (E.g., {A->B, B->C}) are legal.
 * Cyclic mappings may not be detected.
 *
 * This is for use by PLN admins when changing IP of a node.
 */
public class ReputationTransfers {
  private static final Logger log = Logger.getLogger("ReputationTransfers");

  private static final String PREFIX = Configuration.PREFIX + "poll.v3.";

  public static final String PARAM_REPUTATION_TRANSFER_MAP =
    PREFIX + "reputationTransferMap";

  /** A map, indexed by new PID, of the old PID. The value of map will
   * change when the configuration changes. */
  private Map<PeerIdentity, PeerIdentity> map;

  /** The IdentityManager */
  private final IdentityManager idManager;

 /**
   * Make an unmodifiable view of a HashMap containing the reputation
   * transfers present in peerPairs. Ignore the second time a peer is
   * listed as a source or destination.
   */
  private Map<PeerIdentity, PeerIdentity>
    makeMap(Collection<String> peerPairs, IdentityManager idManager) {
    if (peerPairs == null) {
      return Collections.EMPTY_MAP;
    } else {
      HashMap<PeerIdentity, PeerIdentity> map =
	new HashMap<PeerIdentity, PeerIdentity>();
      for (String onePair : peerPairs) {
	// discardEmptyStrings = true, trimEachString = true
	List<String> list = StringUtil.breakAt(onePair, ',', -1, true, true);
	if (list.size() == 2) {
	  try {
	    PeerIdentity oldPid = idManager.stringToPeerIdentity(list.get(0));
	    PeerIdentity newPid = idManager.stringToPeerIdentity(list.get(1));
	    if (oldPid == newPid) {
	      log.warning("Trying to extend a peer's reputation to itself: "+
			  oldPid);
	      continue;
	    }
	    if (map.containsKey(newPid)) {
	      log.warning("Ignoring second transfer from "+oldPid+" to "+newPid+
			  ". Keeping "+oldPid+" to "+map.get(oldPid)+".");
	      continue;
	    }
	    if (map.containsValue(oldPid)) {
	      log.warning("Ignoring second transfer from "+oldPid+" to "+newPid+
			  ". "+newPid+" has a reputation donor.");
	      continue;
	    }
	    map.put(newPid, oldPid);
	    if (log.isDebug2()) {
	      log.debug2("Extend reputation from " + oldPid + " to " + newPid);
	    }
	  } catch (IdentityManager.MalformedIdentityKeyException e) {
	    log.warning("Bad peer id in peer2peer map entry "+list, e);
	  }
	} else {
	  log.warning("Malformed reputation mapping: " + onePair);
	}
      }
      return Collections.unmodifiableMap(map);
    }
  }

  public ReputationTransfers(IdentityManager idManager) {
    this.idManager = idManager;
    // In production, the PollManager's initial setConfig call will
    // end up calling our setConfig, and setMap there. This setMap is
    // needed for testing.
    setMap();
  }

  /**
   * Update from the changed configuration, if needed.
   */
  public void setConfig(Configuration newConfig,
			Configuration oldConfig,
			Configuration.Differences changedKeys) {
    if (changedKeys.contains(PARAM_REPUTATION_TRANSFER_MAP)) {
      setMap(newConfig.getList(PARAM_REPUTATION_TRANSFER_MAP));
    }
  }

  /** Set the map from the parameter in CurrentConfig. */
  private void setMap() {
    setMap(CurrentConfig.getList(PARAM_REPUTATION_TRANSFER_MAP));
  }

  /** Set the map from the given parameter. */
  private void setMap(Collection<String> mapParam) {
    this.map = makeMap(mapParam, idManager);
  }

  /**
   * Find the old peer, if any, which was in the transfer map
   * parameter as "old peer, new peer".
   *
   * @param pid the PeerIdentity of the new peer.
   * @return the PeerIdentity that transferred its reputation to pid;
   * null if none exists.
   */
  PeerIdentity getReputationTransferredFrom(PeerIdentity newPid) {
    // todo(bhayes): deprecate this, and pre-calculate the Collections
    // returned below, rather than just the raw Map?
    return map.get(newPid);
  }

  /**
   * Return the transitive closure of all the peers which have their
   * reputation transfered to the "new peer".
   *
   * Note: At the moment, this list will never be empty, and will
   * always include the input PeerIdentity. However, callers should
   * not rely on this.
   *
   * @param pid the PeerIndentity of the new peer.
   * @return all the peers whose reputation contributes to the
   * reputation of the "new peer".
   */
  public Collection<PeerIdentity>
      getAllReputationsTransferredFrom(PeerIdentity newPid) {
    Collection<PeerIdentity> pids = new ArrayList<PeerIdentity>();
    while (newPid != null) {
      pids.add(newPid);
      newPid = getReputationTransferredFrom(newPid);
      // Found a loop; stop.
      if (pids.contains(newPid)) {
	log.warning("Found cycle: "+pids);
	break;
      }
    }
    return pids;
  }
}
