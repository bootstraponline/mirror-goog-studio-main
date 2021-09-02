
package com.android.sdklib.repository.generated.addon.v3;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;

import com.android.repository.api.Repository;
import com.android.repository.impl.generated.v2.RepositoryType;
import com.android.sdklib.repository.meta.AddonFactory;


/**
 * DO NOT EDIT This file was generated by xjc from sdk-addon-03.xsd. Any changes will be lost upon
 * recompilation of the schema. See the schema file for instructions on running xjc.
 *
 * This object contains factory methods for each Java content interface and Java element interface
 * generated in the com.android.sdklib.repository.generated.addon.v3 package.
 * <p>An ObjectFactory allows you to programatically
 * construct new instances of the Java representation for XML content. The Java representation of
 * XML content can consist of schema derived interfaces and classes representing the binding of
 * schema type definitions, element declarations and model groups.  Factory methods for each of
 * these are provided in this class.
 */
@XmlRegistry
@SuppressWarnings("override")
public class ObjectFactory
        extends AddonFactory {

    private final static QName _SdkAddon_QNAME = new QName(
            "http://schemas.android.com/sdk/android/repo/addon2/03",
            "sdk-addon");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes
     * for package: com.android.sdklib.repository.generated.addon.v3
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link AddonDetailsType }
     */
    public AddonDetailsType createAddonDetailsType() {
        return new AddonDetailsType();
    }

    /**
     * Create an instance of {@link LibrariesType }
     */
    public LibrariesType createLibrariesType() {
        return new LibrariesType();
    }

    /**
     * Create an instance of {@link ExtraDetailsType }
     */
    public ExtraDetailsType createExtraDetailsType() {
        return new ExtraDetailsType();
    }

    /**
     * Create an instance of {@link MavenType }
     */
    public MavenType createMavenType() {
        return new MavenType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RepositoryType }{@code >}
     *
     * @param value Java instance representing xml element's value.
     * @return the new instance of {@link JAXBElement }{@code <}{@link RepositoryType }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.android.com/sdk/android/repo/addon2/03", name = "sdk-addon")
    public JAXBElement<RepositoryType> createSdkAddonInternal(RepositoryType value) {
        return new JAXBElement<RepositoryType>(_SdkAddon_QNAME, RepositoryType.class, null, value);
    }

    public JAXBElement<Repository> generateSdkAddon(Repository value) {
        return ((JAXBElement)createSdkAddonInternal(((RepositoryType)value)));
    }
}
