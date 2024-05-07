/*
 * Copyright (C) 2016-2021 Frode Randers
 * All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gautelis.dicom.xpath;

import org.gautelis.dicom.model.DicomAttribute;
import org.gautelis.dicom.model.DicomDocument;
import org.gautelis.dicom.model.DicomElement;
import org.jaxen.DefaultNavigator;
import org.jaxen.FunctionCallException;
import org.jaxen.JaxenConstants;
import org.jaxen.UnsupportedAxisException;
import org.jaxen.saxpath.SAXPathException;
import org.jaxen.util.SingleObjectIterator;

import java.util.ArrayList;
import java.util.Iterator;

//------------------------------------------------
// DicomDocument corresponds to an XML Document
// DicomElement corresponds to an XML Element
// DicomAttribute corresponds to an XML Attribute
//------------------------------------------------
public class DicomNavigator extends DefaultNavigator {
    private static final long serialVersionUID = -20160202113000L;

    public DicomNavigator() {
    }

    public org.jaxen.XPath parseXPath(String xpath) throws SAXPathException {
        return new XPath(xpath, this);
    }

    public String getElementNamespaceUri(Object object) {
        return null;
    }

    public String getElementName(Object object) {
        if (object instanceof DicomElement) {
            return ((DicomElement)object).getName();
        }
        return null;
    }

    public String getElementQName(Object object) {
        return getElementName(object);
    }

    public String getAttributeNamespaceUri(Object object) {
        return null;
    }

    public String getAttributeName(Object object) {
        if (object instanceof DicomAttribute) {
            return ((DicomAttribute)object).getName();
        }
        return null;
    }

    public String getAttributeQName(Object object) {
        return getAttributeName(object);
    }

    public boolean isDocument(Object object) {
        return object instanceof DicomDocument;
    }

    public boolean isElement(Object object) {
        return object instanceof DicomElement;
    }

    public boolean isAttribute(Object object) {
        return object instanceof DicomAttribute;
    }

    public boolean isNamespace(Object object) {
        return false;
    }

    public boolean isComment(Object object) {
        return false;
    }

    public boolean isText(Object object) {
        return false;
    }

    public boolean isProcessingInstruction(Object object) {
        return false;
    }

    public String getCommentStringValue(Object object) {
        return null;
    }

    public String getElementStringValue(Object object) {
        // DicomElement does not have any value(s)
        return null;
    }

    public String getAttributeStringValue(Object object) {
        if (object instanceof DicomAttribute) {
            return ((DicomAttribute)object).getValue();
        }
        return null;
    }

    public String getNamespaceStringValue(Object object) {
        return null;
    }

    public String getTextStringValue(Object object) {
        return null;
    }

    public String getNamespacePrefix(Object object) {
        return null;
    }

    public Iterator getChildAxisIterator(Object contextNode) {
        if (contextNode instanceof DicomDocument) {
            return ((DicomDocument)contextNode).getRootElement().getChildIterator();

        } else if (contextNode instanceof DicomElement) {
            return ((DicomElement)contextNode).getChildIterator();
        }
        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Iterator getDescendantAxisIterator(Object object) throws UnsupportedAxisException {
        return super.getDescendantAxisIterator(object);
    }

    public Iterator getAttributeAxisIterator(Object contextNode) {
        if (contextNode instanceof DicomElement) {
            return ((DicomElement)contextNode).getDicomElements().iterator();
        }
        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Iterator getNamespaceAxisIterator(Object contextNode) {
        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Iterator getSelfAxisIterator(Object contextNode) throws UnsupportedAxisException {
        return super.getSelfAxisIterator(contextNode);
    }

    public Iterator getDescendantOrSelfAxisIterator(Object contextNode) throws UnsupportedAxisException {
        return super.getDescendantOrSelfAxisIterator(contextNode);
    }

    public Iterator getAncestorOrSelfAxisIterator(Object contextNode) throws UnsupportedAxisException {
        return super.getAncestorOrSelfAxisIterator(contextNode);
    }

    public Iterator getParentAxisIterator(Object contextNode) throws UnsupportedAxisException {

        Object parent = getParentNode(contextNode);
        if (null != parent) {
            return new SingleObjectIterator(parent);
        }

        return JaxenConstants.EMPTY_ITERATOR;
    }

    public Iterator getAncestorAxisIterator(Object contextNode) throws UnsupportedAxisException {
        return super.getAncestorAxisIterator(contextNode);
    }

    public Iterator getFollowingSiblingAxisIterator(Object contextNode) {
        ArrayList list = new ArrayList();
        if (contextNode instanceof DicomElement) {
            DicomElement parent = ((DicomElement)contextNode).getOwner();
            if (null == parent) {
                return JaxenConstants.EMPTY_ITERATOR;
            }

            Iterator<DicomElement> sit = parent.getChildIterator();
            while (sit.hasNext()) {
                DicomElement sibling = sit.next();
                if (sibling.equals(contextNode)) {
                    break;
                }
            }

            while (sit.hasNext()) {
                DicomElement followingSibling = sit.next();
                list.add(followingSibling);
            }
        } else if (contextNode instanceof DicomAttribute) {
            DicomElement owner = ((DicomAttribute)contextNode).getOwner();

            Iterator<DicomAttribute> dit = owner.getDicomElements().iterator();
            while (dit.hasNext()) {
                DicomAttribute sibling = dit.next();
                if (sibling.equals(contextNode)) {
                    break;
                }
            }

            while (dit.hasNext()) {
                DicomAttribute followingSibling = dit.next();
                list.add(followingSibling);
            }
        }
        // when contextNode instanceof DicomFile, we don't have any siblings

        return list.iterator();
    }

    public Iterator getPrecedingSiblingAxisIterator(Object contextNode) {
        ArrayList list = new ArrayList();
        if (contextNode instanceof DicomElement) {
            DicomElement parent = ((DicomElement)contextNode).getOwner();
            if (null == parent) {
                return JaxenConstants.EMPTY_ITERATOR;
            }

            Iterator<DicomElement> sit = parent.getChildIterator();
            while (sit.hasNext()) {
                DicomElement sibling = sit.next();
                if (!sibling.equals(contextNode)) {
                    list.add(sibling);
                } else {
                    break;
                }
            }
        } else if (contextNode instanceof DicomAttribute) {
            DicomElement owner = ((DicomAttribute)contextNode).getOwner();

            for (DicomAttribute sibling : owner.getDicomElements()) {
                if (!sibling.equals(contextNode)) {
                    list.add(sibling);
                } else {
                    break;
                }
            }
        }
        // when contextNode instanceof DicomFile, we don't have any siblings

        return list.iterator();
    }

    public Iterator getFollowingAxisIterator(Object contextNode) throws UnsupportedAxisException {
        return super.getFollowingAxisIterator(contextNode);
    }

    public Iterator getPrecedingAxisIterator(Object contextNode) throws UnsupportedAxisException {
        return super.getPrecedingAxisIterator(contextNode);
    }

    public Object getDocument(String uri) {
        return null;
    }

    public Object getElementById(Object contextNode, String elementId) {
        return super.getElementById(contextNode, elementId);
    }

    public Object getDocumentNode(Object contextNode) {
        if (contextNode instanceof DicomDocument) {
            return ((DicomDocument)contextNode).getRootElement();

        } else if (contextNode instanceof DicomElement) {

            DicomElement dicomElement = ((DicomElement) contextNode);
            DicomElement parent;
            do {
                parent = dicomElement.getOwner();
                if (null != parent) {
                    dicomElement = parent;
                }
            } while (null != parent);

            return dicomElement; // top-most
        }
        return null;
    }

    public String translateNamespacePrefixToUri(String prefix, Object element) {
        return super.translateNamespacePrefixToUri(prefix, element);
    }

    public String getProcessingInstructionTarget(Object object) {
        return null;
    }

    public String getProcessingInstructionData(Object object) {
        return null;
    }

    public short getNodeType(Object node) {
        return super.getNodeType(node);
    }

    public Object getParentNode(Object contextNode) {
        if (contextNode instanceof DicomElement) {
            return ((DicomElement)contextNode).getOwner();

        } else if (contextNode instanceof DicomAttribute) {
            return ((DicomAttribute)contextNode).getOwner();
        }

        return null; // including when contextNode instanceof DicomFile
    }
}
