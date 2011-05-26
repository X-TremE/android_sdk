/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.internal.repository;

import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.Archive.Arch;
import com.android.sdklib.internal.repository.Archive.Os;
import com.android.sdklib.repository.SdkRepoConstants;

import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

/**
 * A {@link Package} is the base class for "something" that can be downloaded from
 * the SDK repository.
 * <p/>
 * A package has some attributes (revision, description) and a list of archives
 * which represent the downloadable bits.
 * <p/>
 * Packages are contained by a {@link SdkSource} (a download site).
 * <p/>
 * Derived classes must implement the {@link IDescription} methods.
 */
public abstract class Package implements IDescription, Comparable<Package> {

    public static final String PROP_REVISION     = "Pkg.Revision";     //$NON-NLS-1$
    public static final String PROP_LICENSE      = "Pkg.License";      //$NON-NLS-1$
    public static final String PROP_DESC         = "Pkg.Desc";         //$NON-NLS-1$
    public static final String PROP_DESC_URL     = "Pkg.DescUrl";      //$NON-NLS-1$
    public static final String PROP_RELEASE_NOTE = "Pkg.RelNote";      //$NON-NLS-1$
    public static final String PROP_RELEASE_URL  = "Pkg.RelNoteUrl";   //$NON-NLS-1$
    public static final String PROP_SOURCE_URL   = "Pkg.SourceUrl";    //$NON-NLS-1$
    public static final String PROP_OBSOLETE     = "Pkg.Obsolete";     //$NON-NLS-1$

    private final int mRevision;
    private final String mObsolete;
    private final String mLicense;
    private final String mDescription;
    private final String mDescUrl;
    private final String mReleaseNote;
    private final String mReleaseUrl;
    private final Archive[] mArchives;
    private final SdkSource mSource;

    /**
     * Enum for the result of {@link Package#canBeUpdatedBy(Package)}. This used so that we can
     * differentiate between a package that is totally incompatible, and one that is the same item
     * but just not an update.
     * @see #canBeUpdatedBy(Package)
     */
    public static enum UpdateInfo {
        /** Means that the 2 packages are not the same thing */
        INCOMPATIBLE,
        /** Means that the 2 packages are the same thing but one does not upgrade the other */
        NOT_UPDATE,
        /** Means that the 2 packages are the same thing, and one is the upgrade of the other */
        UPDATE;
    }

    /**
     * Creates a new package from the attributes and elements of the given XML node.
     * This constructor should throw an exception if the package cannot be created.
     *
     * @param source The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri The namespace URI of the originating XML document, to be able to deal with
     *          parameters that vary according to the originating XML schema.
     * @param licenses The licenses loaded from the XML originating document.
     */
    Package(SdkSource source, Node packageNode, String nsUri, Map<String,String> licenses) {
        mSource = source;
        mRevision    = XmlParserUtils.getXmlInt   (packageNode, SdkRepoConstants.NODE_REVISION, 0);
        mDescription = XmlParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_DESCRIPTION);
        mDescUrl     = XmlParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_DESC_URL);
        mReleaseNote = XmlParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_RELEASE_NOTE);
        mReleaseUrl  = XmlParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_RELEASE_URL);
        mObsolete    = XmlParserUtils.getOptionalXmlString(
                                                   packageNode, SdkRepoConstants.NODE_OBSOLETE);

        mLicense  = parseLicense(packageNode, licenses);
        mArchives = parseArchives(XmlParserUtils.getFirstChild(
                                  packageNode, SdkRepoConstants.NODE_ARCHIVES));
    }

    /**
     * Manually create a new package with one archive and the given attributes.
     * This is used to create packages from local directories in which case there must be
     * one archive which URL is the actual target location.
     * <p/>
     * Properties from props are used first when possible, e.g. if props is non null.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    public Package(
            SdkSource source,
            Properties props,
            int revision,
            String license,
            String description,
            String descUrl,
            Os archiveOs,
            Arch archiveArch,
            String archiveOsPath) {

        if (description == null) {
            description = "";
        }
        if (descUrl == null) {
            descUrl = "";
        }

        mRevision = Integer.parseInt(getProperty(props, PROP_REVISION, Integer.toString(revision)));
        mLicense     = getProperty(props, PROP_LICENSE,      license);
        mDescription = getProperty(props, PROP_DESC,         description);
        mDescUrl     = getProperty(props, PROP_DESC_URL,     descUrl);
        mReleaseNote = getProperty(props, PROP_RELEASE_NOTE, "");
        mReleaseUrl  = getProperty(props, PROP_RELEASE_URL,  "");
        mObsolete    = getProperty(props, PROP_OBSOLETE,     null);

        // If source is null and we can find a source URL in the properties, generate
        // a dummy source just to store the URL. This allows us to easily remember where
        // a package comes from.
        String srcUrl = getProperty(props, PROP_SOURCE_URL, null);
        if (props != null && source == null && srcUrl != null) {
            if (this instanceof AddonPackage) {
                source = new SdkAddonSource(srcUrl, null /*uiName*/);
            } else {
                source = new SdkRepoSource(srcUrl, null /*uiName*/);
            }
        }
        mSource = source;

        mArchives = new Archive[1];
        mArchives[0] = createLocalArchive(props, archiveOs, archiveArch, archiveOsPath);
    }

    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected Archive createLocalArchive(
            Properties props,
            Os archiveOs,
            Arch archiveArch,
            String archiveOsPath) {
        return new Archive(this,
                props,
                archiveOs,
                archiveArch,
                archiveOsPath);
    }

    /**
     * Utility method that returns a property from a {@link Properties} object.
     * Returns the default value if props is null or if the property is not defined.
     *
     * @param props The {@link Properties} to search into.
     *   If null, the default value is returned.
     * @param propKey The name of the property. Must not be null.
     * @param defaultValue The default value to return if {@code props} is null or if the
     *   key is not found. Can be null.
     * @return The string value of the given key in the properties, or null if the key
     *   isn't found or if {@code props} is null.
     */
    static String getProperty(Properties props, String propKey, String defaultValue) {
        if (props == null) {
            return defaultValue;
        }
        return props.getProperty(propKey, defaultValue);
    }

    /**
     * Save the properties of the current packages in the given {@link Properties} object.
     * These properties will later be give the constructor that takes a {@link Properties} object.
     */
    void saveProperties(Properties props) {
        props.setProperty(PROP_REVISION, Integer.toString(mRevision));
        if (mLicense != null && mLicense.length() > 0) {
            props.setProperty(PROP_LICENSE, mLicense);
        }

        if (mDescription != null && mDescription.length() > 0) {
            props.setProperty(PROP_DESC, mDescription);
        }
        if (mDescUrl != null && mDescUrl.length() > 0) {
            props.setProperty(PROP_DESC_URL, mDescUrl);
        }

        if (mReleaseNote != null && mReleaseNote.length() > 0) {
            props.setProperty(PROP_RELEASE_NOTE, mReleaseNote);
        }
        if (mReleaseUrl != null && mReleaseUrl.length() > 0) {
            props.setProperty(PROP_RELEASE_URL, mReleaseUrl);
        }
        if (mObsolete != null) {
            props.setProperty(PROP_OBSOLETE, mObsolete);
        }

        if (mSource != null) {
            props.setProperty(PROP_SOURCE_URL,  mSource.getUrl());
        }
    }

    /**
     * Parses the uses-licence node of this package, if any, and returns the license
     * definition if there's one. Returns null if there's no uses-license element or no
     * license of this name defined.
     */
    private String parseLicense(Node packageNode, Map<String, String> licenses) {
        Node usesLicense = XmlParserUtils.getFirstChild(
                                            packageNode, SdkRepoConstants.NODE_USES_LICENSE);
        if (usesLicense != null) {
            Node ref = usesLicense.getAttributes().getNamedItem(SdkRepoConstants.ATTR_REF);
            if (ref != null) {
                String licenseRef = ref.getNodeValue();
                return licenses.get(licenseRef);
            }
        }
        return null;
    }

    /**
     * Parses an XML node to process the <archives> element.
     */
    private Archive[] parseArchives(Node archivesNode) {
        ArrayList<Archive> archives = new ArrayList<Archive>();

        if (archivesNode != null) {
            String nsUri = archivesNode.getNamespaceURI();
            for(Node child = archivesNode.getFirstChild();
                     child != null;
                     child = child.getNextSibling()) {

                if (child.getNodeType() == Node.ELEMENT_NODE &&
                        nsUri.equals(child.getNamespaceURI()) &&
                        SdkRepoConstants.NODE_ARCHIVE.equals(child.getLocalName())) {
                    archives.add(parseArchive(child));
                }
            }
        }

        return archives.toArray(new Archive[archives.size()]);
    }

    /**
     * Parses one <archive> element from an <archives> container.
     */
    private Archive parseArchive(Node archiveNode) {
        Archive a = new Archive(
                    this,
                    (Os)   XmlParserUtils.getEnumAttribute(archiveNode, SdkRepoConstants.ATTR_OS,
                            Os.values(), null),
                    (Arch) XmlParserUtils.getEnumAttribute(archiveNode, SdkRepoConstants.ATTR_ARCH,
                            Arch.values(), Arch.ANY),
                    XmlParserUtils.getXmlString(archiveNode, SdkRepoConstants.NODE_URL),
                    XmlParserUtils.getXmlLong  (archiveNode, SdkRepoConstants.NODE_SIZE, 0),
                    XmlParserUtils.getXmlString(archiveNode, SdkRepoConstants.NODE_CHECKSUM)
                );

        return a;
    }

    /**
     * Returns the source that created (and owns) this package. Can be null.
     */
    public SdkSource getParentSource() {
        return mSource;
    }

    /**
     * Returns true if the package is deemed obsolete, that is it contains an
     * actual <code>&lt;obsolete&gt;</code> element.
     */
    public boolean isObsolete() {
        return mObsolete != null;
    }

    /**
     * Returns the revision, an int > 0, for all packages (platform, add-on, tool, doc).
     * Can be 0 if this is a local package of unknown revision.
     */
    public int getRevision() {
        return mRevision;
    }

    /**
     * Returns the optional description for all packages (platform, add-on, tool, doc) or
     * for a lib. It is null if the element has not been specified in the repository XML.
     */
    public String getLicense() {
        return mLicense;
    }

    /**
     * Returns the optional description for all packages (platform, add-on, tool, doc) or
     * for a lib. Can be empty but not null.
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Returns the optional description URL for all packages (platform, add-on, tool, doc).
     * Can be empty but not null.
     */
    public String getDescUrl() {
        return mDescUrl;
    }

    /**
     * Returns the optional release note for all packages (platform, add-on, tool, doc) or
     * for a lib. Can be empty but not null.
     */
    public String getReleaseNote() {
        return mReleaseNote;
    }

    /**
     * Returns the optional release note URL for all packages (platform, add-on, tool, doc).
     * Can be empty but not null.
     */
    public String getReleaseNoteUrl() {
        return mReleaseUrl;
    }

    /**
     * Returns the archives defined in this package.
     * Can be an empty array but not null.
     */
    public Archive[] getArchives() {
        return mArchives;
    }

    /**
     * Returns whether the {@link Package} has at least one {@link Archive} compatible with
     * the host platform.
     */
    public boolean hasCompatibleArchive() {
        for (Archive archive : mArchives) {
            if (archive.isCompatible()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the short description of the source, if not null.
     * Otherwise returns the default Object toString result.
     * <p/>
     * This is mostly helpful for debugging.
     * For UI display, use the {@link IDescription} interface.
     */
    @Override
    public String toString() {
        String s = getShortDescription();
        if (s != null) {
            return s;
        }
        return super.toString();
    }

    /**
     * Returns a description of this package that is suitable for a list display.
     * Should not be empty. Must never be null.
     * <p/>
     * Note that this is the "base" name for the package
     * with no specific revision nor API mentionned.
     * In contrast, {@link #getShortDescription()} should be used if you want more details
     * such as the package revision number or the API, if applicable.
     */
    public abstract String getListDescription();

    /**
     * Returns a short description for an {@link IDescription}.
     * Can be empty but not null.
     */
    public abstract String getShortDescription();

    /**
     * Returns a long description for an {@link IDescription}.
     * Can be empty but not null.
     */
    public String getLongDescription() {
        StringBuilder sb = new StringBuilder();

        String s = getDescription();
        if (s != null) {
            sb.append(s);
        }
        if (sb.length() > 0) {
            sb.append("\n");
        }

        sb.append(String.format("Revision %1$d%2$s",
                getRevision(),
                isObsolete() ? " (Obsolete)" : ""));

        s = getDescUrl();
        if (s != null && s.length() > 0) {
            sb.append(String.format("\n\nMore information at %1$s", s));
        }

        s = getReleaseNote();
        if (s != null && s.length() > 0) {
            sb.append("\n\nRelease note:\n").append(s);
        }

        s = getReleaseNoteUrl();
        if (s != null && s.length() > 0) {
            sb.append("\nRelease note URL: ").append(s);
        }

        return sb.toString();
    }

    /**
     * Computes a potential installation folder if an archive of this package were
     * to be installed right away in the given SDK root.
     * <p/>
     * Some types of packages install in a fix location, for example docs and tools.
     * In this case the returned folder may already exist with a different archive installed
     * at the desired location. <br/>
     * For other packages types, such as add-on or platform, the folder name is only partially
     * relevant to determine the content and thus a real check will be done to provide an
     * existing or new folder depending on the current content of the SDK.
     * <p/>
     * Note that the installer *will* create all directories returned here just before
     * installation so this method must not attempt to create them.
     *
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    public abstract File getInstallFolder(String osSdkRoot, SdkManager sdkManager);

    /**
     * Hook called right before an archive is installed. The archive has already
     * been downloaded successfully and will be installed in the directory specified by
     * <var>installFolder</var> when this call returns.
     * <p/>
     * The hook lets the package decide if installation of this specific archive should
     * be continue. The installer will still install the remaining packages if possible.
     * <p/>
     * The base implementation always return true.
     * <p/>
     * Note that the installer *will* create all directories specified by
     * {@link #getInstallFolder} just before installation, so they must not be
     * created here. This is also called before the previous install dir is removed
     * so the previous content is still there during upgrade.
     *
     * @param archive The archive that will be installed
     * @param monitor The {@link ITaskMonitor} to display errors.
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param installFolder The folder where the archive will be installed. Note that this
     *                      is <em>not</em> the folder where the archive was temporary
     *                      unzipped. The installFolder, if it exists, contains the old
     *                      archive that will soon be replaced by the new one.
     * @return True if installing this archive shall continue, false if it should be skipped.
     */
    public boolean preInstallHook(Archive archive, ITaskMonitor monitor,
            String osSdkRoot, File installFolder) {
        // Nothing to do in base class.
        return true;
    }

    /**
     * Hook called right after an archive has been installed.
     *
     * @param archive The archive that has been installed.
     * @param monitor The {@link ITaskMonitor} to display errors.
     * @param installFolder The folder where the archive was successfully installed.
     *                      Null if the installation failed, in case the archive needs to
     *                      do some cleanup after <code>preInstallHook</code>.
     */
    public void postInstallHook(Archive archive, ITaskMonitor monitor, File installFolder) {
        // Nothing to do in base class.
    }

    /**
     * Returns whether the give package represents the same item as the current package.
     * <p/>
     * Two packages are considered the same if they represent the same thing, except for the
     * revision number.
     * @param pkg the package to compare
     * @return true if the item
     */
    public abstract boolean sameItemAs(Package pkg);

    /**
     * Computes whether the given package is a suitable update for the current package.
     * <p/>
     * An update is just that: a new package that supersedes the current one. If the new
     * package does not represent the same item or if it has the same or lower revision as the
     * current one, it's not an update.
     *
     * @param replacementPackage The potential replacement package.
     * @return One of the {@link UpdateInfo} values.
     *
     * @see #sameItemAs(Package)
     */
    public UpdateInfo canBeUpdatedBy(Package replacementPackage) {
        if (replacementPackage == null) {
            return UpdateInfo.INCOMPATIBLE;
        }

        // check they are the same item.
        if (sameItemAs(replacementPackage) == false) {
            return UpdateInfo.INCOMPATIBLE;
        }

        // check revision number
        if (replacementPackage.getRevision() > this.getRevision()) {
            return UpdateInfo.UPDATE;
        }

        // not an upgrade but not incompatible either.
        return UpdateInfo.NOT_UPDATE;
    }


    /**
     * Returns an ordering like this: <br/>
     * - Tools <br/>
     * - Platform-Tools <br/>
     * - Docs. <br/>
     * - Platform n preview <br/>
     * - Platform n <br/>
     * - Platform n-1 <br/>
     * - Samples packages <br/>
     * - Add-on based on n preview <br/>
     * - Add-on based on n <br/>
     * - Add-on based on n-1 <br/>
     * - Extra packages <br/>
     * <p/>
     * Important: this must NOT be used to compare if two packages are the same thing.
     * This is achieved by {@link #sameItemAs(Package)} or {@link #canBeUpdatedBy(Package)}.
     * <p/>
     * This {@link #compareTo(Package)} method is purely an implementation detail to
     * perform the right ordering of the packages in the list of available or installed packages.
     */
    public int compareTo(Package other) {
        int s1 = this.sortingScore();
        int s2 = other.sortingScore();
        return s1 - s2;
    }

    /**
     * Computes the score for each package used by {@link #compareTo(Package)}.
     */
    private int sortingScore() {
        // up to 31 bits (for signed stuff)
        int type = 0;             // max type=7 => 3 bits
        int rev = getRevision();  // 12 bits... 4095
        int offset = 0;           // 16 bits...
        if (this instanceof ToolPackage) {
            type = 6;
        } else if (this instanceof PlatformToolPackage) {
            type = 5;
        } else if (this instanceof DocPackage) {
            type = 4;
        } else if (this instanceof PlatformPackage) {
            type = 3;
        } else if (this instanceof SamplePackage) {
            type = 2;
        } else if (this instanceof AddonPackage) {
            type = 1;
        } else {
            // extras and everything else
            type = 0;
        }

        if (this instanceof IPackageVersion) {
            AndroidVersion v = ((IPackageVersion) this).getVersion();
            offset = v.getApiLevel();
            offset = offset * 2 + (v.isPreview() ? 1 : 0);
        }

        int n = (type << 28) + (offset << 12) + rev;
        return 0 - n;
    }

}
