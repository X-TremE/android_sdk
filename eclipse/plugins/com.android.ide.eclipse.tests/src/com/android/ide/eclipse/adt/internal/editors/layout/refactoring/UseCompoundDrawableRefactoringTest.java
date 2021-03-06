/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.editors.layout.refactoring;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ltk.core.refactoring.Change;
import org.w3c.dom.Element;

import java.util.List;

@SuppressWarnings("javadoc")
public class UseCompoundDrawableRefactoringTest extends RefactoringTest {

    public void test1() throws Exception {
        // Test converting an image above a text view
        checkRefactoring("refactoring/usecompound/compound1.xml", "@+id/layout1");
    }

    public void test2() throws Exception {
        // Test converting an image below a text view
        checkRefactoring("refactoring/usecompound/compound2.xml", "@+id/layout2");
    }

    public void test3() throws Exception {
        // Test converting an image to the left of a text view
        checkRefactoring("refactoring/usecompound/compound3.xml", "@+id/layout3");
    }

    public void test4() throws Exception {
        // Test converting an image to the right of a text view
        checkRefactoring("refactoring/usecompound/compound4.xml", "@+id/layout4");
    }

    public void test5() throws Exception {
        // Test converting an image where the LinearLayout is referenced (in a relative layout)
        // and the text view has an id
        checkRefactoring("refactoring/usecompound/compound_all.xml", "@+id/layout2");
    }

    public void test6() throws Exception {
        // Test converting an image where the LinearLayout is referenced (in a relative layout)
        // and the text view does not have an id
        checkRefactoring("refactoring/usecompound/compound_all.xml", "@+id/layout3");
    }

    public void test7() throws Exception {
        // Test converting where a namespace needs to be migrated
        checkRefactoring("refactoring/usecompound/compound5.xml", "@+id/layout");
    }

    private void checkRefactoring(String basename, String id) throws Exception {
        IFile file = getLayoutFile(getProject(), basename);
        TestContext info = setupTestContext(file, basename);
        TestLayoutEditorDelegate layoutEditor = info.mLayoutEditorDelegate;
        List<Element> selectedElements = getElements(info.mElement, new String[] { id });

        UseCompoundDrawableRefactoring refactoring = new UseCompoundDrawableRefactoring(
                selectedElements, layoutEditor);
        List<Change> changes = refactoring.computeChanges(new NullProgressMonitor());
        checkEdits(basename, changes);
    }
}
