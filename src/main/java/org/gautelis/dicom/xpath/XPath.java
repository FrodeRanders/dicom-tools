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

import org.gautelis.dicom.model.DicomElement;
import org.jaxen.BaseXPath;
import org.jaxen.JaxenException;
import org.jaxen.Navigator;

public class XPath extends BaseXPath {
    private static final long serialVersionUID = -20160202113000L;

    public XPath(String xpathExpr) throws JaxenException {
        super(xpathExpr, new DicomNavigator());
    }

    public XPath(String xpathExpr, Navigator navigator) throws JaxenException {
        super(xpathExpr, navigator);
    }

    private XPath(DicomElement dicomElement, String xpathExpr) throws JaxenException {
        this(xpathExpr);
    }
}
