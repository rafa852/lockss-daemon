/*
 * $Id: TestScHtmlLinkExtractor.java 39864 2015-02-18 09:10:24Z thib_gc $
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

package org.lockss.plugin.silverchair;

import java.util.*;

import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.extractor.LinkExtractor;
import org.lockss.plugin.definable.DefinablePlugin;
import org.lockss.test.*;
import org.lockss.util.Constants;

public class TestScJsonLinkExtractor extends LockssTestCase {

	public static final String MIME_TYPE_JSON = "application/json";
	public static final String BASE_URL = "http://www.example.com";
	public static final String HTTPS_BASE_URL = "https://www.example.com";

	static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
	static final String YEAR_KEY = ConfigParamDescr.YEAR.getKey();
	static final String RES_ID_KEY = "resource_id";
	private DefinablePlugin plugin;
	private MockArchivalUnit http_mau;
	private MockArchivalUnit https_mau2;

	public void setUp() throws Exception {
		super.setUp();
		plugin = new DefinablePlugin();
		plugin.initPlugin(getMockLockssDaemon(),
				"org.lockss.plugin.silverchair.ClockssSilverchairJournalsPlugin");
		Properties props = new Properties();
		//http
		props.setProperty(YEAR_KEY, "2016");
		props.setProperty(RES_ID_KEY, "140");
		props.setProperty(BASE_URL_KEY, BASE_URL);
		Configuration config = ConfigurationUtil.fromProps(props);
		http_mau = new MockArchivalUnit();
		http_mau.setConfiguration(config);

		props.setProperty(BASE_URL_KEY, HTTPS_BASE_URL);
		Configuration config2 = ConfigurationUtil.fromProps(props);
		https_mau2 = new MockArchivalUnit();
		https_mau2.setConfiguration(config2);
	}	



	public void testJamaJsonLink() throws Exception {
		String srcUrl = "http://www.example.com/volume.aspx/SetPDFLinkBasedOnAccess";
		String input =
				"{\"d\":\"/pdfaccess.ashx?ResourceID=10507872&PDFSource=24\"}";
		LinkExtractor le = new ScJsonLinkExtractorFactory().createLinkExtractor
				(MIME_TYPE_JSON);

		final List<String> emitted = new ArrayList<>();
		le.extractUrls(http_mau,
				new StringInputStream(input),
				Constants.ENCODING_UTF_8,
				srcUrl,
				new LinkExtractor.Callback() {
			@Override
			public void foundLink(String url) {
				emitted.add(url);
			}
		});
		assertEquals(1, emitted.size());
		assertContains(emitted, BASE_URL +
				"/pdfaccess.ashx?ResourceID=10507872&PDFSource=24");

	}
	
	public void testHttpsJamaJsonLink() throws Exception {
		String srcUrl = "http://www.example.com/volume.aspx/SetPDFLinkBasedOnAccess";
		String input =
				"{\"d\":\"/pdfaccess.ashx?ResourceID=10507872&PDFSource=24\"}";
		LinkExtractor le = new ScJsonLinkExtractorFactory().createLinkExtractor
				(MIME_TYPE_JSON);

		final List<String> emitted = new ArrayList<>();
		le.extractUrls(https_mau2,
				new StringInputStream(input),
				Constants.ENCODING_UTF_8,
				srcUrl,
				new LinkExtractor.Callback() {
			@Override
			public void foundLink(String url) {
				emitted.add(url);
			}
		});
		assertEquals(1, emitted.size());
		assertContains(emitted, HTTPS_BASE_URL +
				"/pdfaccess.ashx?ResourceID=10507872&PDFSource=24");

	}	

	public void testSpieJsonLink() throws Exception {
		String srcUrl = "http://www.example.com/volume.aspx/SetPDFLinkBasedOnAccess";
		String input =
				"{\"d\":{ \"hasAccess\": true," +
						" \"pdfUrl\": \"pdfaccess.ashx?ResourceID=9642913&PDFSource=24\"," +
						" \"itemIsFree\": \"False\"}}";
		input = "{\"d\":\"{ \\\"hasAccess\\\": true, \\\"pdfUrl\\\": \\\"pdfaccess" +
				".ashx?ResourceID=8607807&PDFSource=24\\\", \\\"itemIsFree\\\": \\\"False\\\"}\"}";
		LinkExtractor le = new ScJsonLinkExtractorFactory().createLinkExtractor
				(MIME_TYPE_JSON);

		final List<String> emitted = new ArrayList<>();
		le.extractUrls(http_mau,
				new StringInputStream(input),
				Constants.ENCODING_UTF_8,
				srcUrl,
				new LinkExtractor.Callback() {
			@Override
			public void foundLink(String url) {
				emitted.add(url);
			}
		});
		assertEquals(1, emitted.size());
		assertContains(emitted, BASE_URL+
				"/pdfaccess.ashx?ResourceID=8607807&PDFSource=24");
	}
}
