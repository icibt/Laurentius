
package si.laurentius.outbox.property;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="OutProperty" type="{http://si.laurentius.outbox/property}OutProperty" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "outProperties"
})
@XmlRootElement(name = "OutProperties")
public class OutProperties
    implements Serializable
{

    @XmlElement(name = "OutProperty")
    protected List<OutProperty> outProperties;

    /**
     * Gets the value of the outProperties property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the outProperties property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getOutProperties().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link OutProperty }
     * 
     * 
     */
    public List<OutProperty> getOutProperties() {
        if (outProperties == null) {
            outProperties = new ArrayList<OutProperty>();
        }
        return this.outProperties;
    }

}