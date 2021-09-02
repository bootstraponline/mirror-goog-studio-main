
package com.android.sdklib.repository.generated.addon.v3;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import com.android.repository.impl.generated.v2.TypeDetails;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.generated.common.v3.IdDisplayType;


/**
 * DO NOT EDIT This file was generated by xjc from sdk-addon-03.xsd. Any changes will be lost upon
 * recompilation of the schema. See the schema file for instructions on running xjc.
 *
 *
 * type-details subclass containing vendor information.
 *
 *
 * <p>Java class for extraDetailsType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="extraDetailsType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://schemas.android.com/repository/android/common/02}typeDetails"&gt;
 *       &lt;all&gt;
 *         &lt;element name="vendor" type="{http://schemas.android.com/sdk/android/repo/common/03}idDisplayType"/&gt;
 *       &lt;/all&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "extraDetailsType", propOrder = {
        "vendor"
})
@SuppressWarnings({
        "override",
        "unchecked"
})
public class ExtraDetailsType
        extends TypeDetails
        implements com.android.sdklib.repository.meta.DetailsTypes.ExtraDetailsType {

    @XmlElement(required = true)
    protected IdDisplayType vendor;

    /**
     * Gets the value of the vendor property.
     *
     * @return possible object is {@link IdDisplayType }
     */
    public IdDisplayType getVendor() {
        return vendor;
    }

    /**
     * Sets the value of the vendor property.
     *
     * @param value allowed object is {@link IdDisplayType }
     */
    public void setVendorInternal(IdDisplayType value) {
        this.vendor = value;
    }

    public void setVendor(IdDisplay value) {
        setVendorInternal(((IdDisplayType)value));
    }

    public ObjectFactory createFactory() {
        return new ObjectFactory();
    }
}
