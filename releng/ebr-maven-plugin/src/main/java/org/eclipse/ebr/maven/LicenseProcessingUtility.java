/**
 * Copyright (c) 2014 Tasktop Technologies and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 */
package org.eclipse.ebr.maven;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.ebr.maven.eclipseip.KnownLicense;
import org.eclipse.ebr.maven.eclipseip.KnownLicenses;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

public abstract class LicenseProcessingUtility extends BaseUtility {

	private static final String HTTP_PREFIX = "http://";
	private static final String HTTPS_PREFIX = "https://";

	/** key is groupId + ":" + artifactId */
	private final Map<String, KnownLicense> licensesByArtifact = new HashMap<String, KnownLicense>();

	public LicenseProcessingUtility(final Log log, final MavenSession mavenSession, final boolean force) {
		super(log, mavenSession);
		setForce(force);
	}

	protected void appendDeveloperInfo(final StrBuilder text, final Model artifactPom) {
		boolean first = true;
		for (final Iterator<Developer> stream = artifactPom.getDevelopers().iterator(); stream.hasNext();) {
			final Developer developer = stream.next();
			if (!first && !stream.hasNext()) {
				text.append(" and ");
			} else if (!first && stream.hasNext()) {
				text.append(", ");
			} else {
				first = false;
			}
			text.append(escapeHtml4(developer.getName())).append(" &lt;").append(escapeHtml4(developer.getEmail())).append("&gt;");
			if (StringUtils.isNotBlank(developer.getOrganization())) {
				text.append(" (").append(escapeHtml4(developer.getOrganization())).append(")");
			}
		}
	}

	/**
	 * This will find a known license with either the exact or a similar name.
	 *
	 * @param license
	 * @return
	 * @throws MojoExecutionException
	 */
	private KnownLicense findKnownLicense(final String license) throws MojoExecutionException {
		final KnownLicense l = KnownLicenses.getInstance().findByName(license);
		if (l == null) {
			getLog().error(format("Unable to map license '%s' to a known license.", license));
			logKnownLicenses();
			throw new MojoExecutionException(format("Invalid license '%s'. Please select one that is known in the Eclipse Foundation IP database.", license));
		}
		return l;
	}

	private String getArtifactKey(final Artifact artifact) {
		return artifact.getGroupId() + ":" + artifact.getArtifactId();
	}

	/**
	 * Returns a specific license set via {@link #setLicense(Artifact, String)}.
	 * <p>
	 * Returns <code>null</code> if no specific license was explicitly
	 * configured for the specified artifact
	 * </p>
	 *
	 * @param artifact
	 * @return the license (may be <code>null</code>)
	 */
	public KnownLicense getLicense(final Artifact artifact) {
		return licensesByArtifact.get(getArtifactKey(artifact));
	}

	public KnownLicense getSimilarLicense(final License pomLicense) {
		KnownLicense license = null;

		// try url first
		if (null != pomLicense.getUrl()) {
			license = KnownLicenses.getInstance().findByUrl(pomLicense.getUrl());
		}

		// check name if still null
		if ((license == null) && (null != pomLicense.getName())) {
			license = KnownLicenses.getInstance().findByName(pomLicense.getName());
		}

		return license;
	}

	/**
	 * Indicates if the list of licenses represents a dual or more licensed
	 * artifact.
	 *
	 * @param licenses
	 * @return <code>true</code> if licenses size is greater one or of the one
	 *         license represents a dual license (eg., <i>CDDL+GPL</i>).
	 */
	protected boolean isDualOrMoreLicensed(final List<License> licenses) {
		if (licenses.size() > 1)
			return true;

		if (licenses.size() == 1)
			return KnownLicenses.getInstance().isDualLicense(licenses.get(0));

		return false;
	}

	protected boolean isPotentialWebUrl(final String url) {
		return (url != null) && (StringUtils.startsWithAny(url.toLowerCase(), HTTP_PREFIX, HTTPS_PREFIX) || (StringUtils.indexOf(url, ':') == -1));
	}

	protected void logKnownLicenses() {
		getLog().error("Know licenses are:");
		for (final String name : KnownLicenses.getInstance().getAllLicenseNames()) {
			getLog().error("  - " + name);
		}
	}

	protected String removeWebProtocols(final String url) {
		return StringUtils.removeStart(StringUtils.removeStart(url, HTTPS_PREFIX), HTTP_PREFIX);
	}

	/**
	 * Sets a specific license to use for the specified artifact.
	 *
	 * @param artifact
	 *            the artifact
	 * @param license
	 *            the license
	 * @throws MojoExecutionException
	 */
	public void setLicense(final Artifact artifact, final String license) throws MojoExecutionException {
		getLog().debug(format("Using license '%s' for artifact %s:%s:%s", license, artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
		licensesByArtifact.put(getArtifactKey(artifact), findKnownLicense(license));
	}

	protected URL toUrl(final String url) throws MalformedURLException {
		try {
			return new URL(url);
		} catch (final MalformedURLException e) {
			// try with fall-back using http protocol
			try {
				return toUrl(HTTP_PREFIX + url);
			} catch (final MalformedURLException ignored) {
				// propagate original error
				throw e;
			}
		}
	}
}