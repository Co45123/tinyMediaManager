/*
 * Copyright 2012 - 2015 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.UTF8Control;
import org.tinymediamanager.ui.components.LinkLabel;

import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;

/**
 * The class WhatsNewDialog. Used to show the user a list of changelogs after each upgrade
 * 
 * @author Manuel Laggner
 */
public class WhatsNewDialog extends TmmDialog {
  private static final long           serialVersionUID = -4071143363981892283L;
  /** @wbp.nls.resourceBundle messages */
  private static final ResourceBundle BUNDLE           = ResourceBundle.getBundle("messages", new UTF8Control()); //$NON-NLS-1$
  private static final Logger         LOGGER           = LoggerFactory.getLogger(WhatsNewDialog.class);

  public WhatsNewDialog(String changelog) {
    super(BUNDLE.getString("whatsnew.title"), "whatsnew"); //$NON-NLS-1$
    setSize(500, 250);
    {
      JScrollPane scrollPane = new JScrollPane();
      getContentPane().add(scrollPane, BorderLayout.CENTER);
      JTextPane textPane = new JTextPane();
      textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Globals.settings.getFontSize() + 1));
      scrollPane.setViewportView(textPane);

      textPane.setContentType("text/html");
      textPane.setText(prepareTextAsHtml(changelog));
      textPane.setEditable(false);
      textPane.setCaretPosition(0);
      textPane.addHyperlinkListener(new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent hle) {
          if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
            try {
              TmmUIHelper.browseUrl(hle.getURL().toString());
            }
            catch (Exception e) {
              LOGGER.error("error browsing to " + hle.getURL().toString() + " :" + e.getMessage());
            }
          }
        }
      });
    }
    {
      JPanel panel = new JPanel();
      getContentPane().add(panel, BorderLayout.SOUTH);
      panel.setLayout(new FormLayout(new ColumnSpec[] { FormFactory.RELATED_GAP_COLSPEC, FormFactory.DEFAULT_COLSPEC,
          FormFactory.RELATED_GAP_COLSPEC, FormFactory.DEFAULT_COLSPEC, FormFactory.RELATED_GAP_COLSPEC, ColumnSpec.decode("default:grow"),
          FormFactory.RELATED_GAP_COLSPEC, FormFactory.DEFAULT_COLSPEC, FormFactory.RELATED_GAP_COLSPEC, }, new RowSpec[] {
          FormFactory.LINE_GAP_ROWSPEC, FormFactory.DEFAULT_ROWSPEC, FormFactory.RELATED_GAP_ROWSPEC, }));

      JLabel lblHint = new JLabel(BUNDLE.getString("whatsnew.hint")); //$NON-NLS-1$
      panel.add(lblHint, "2, 2");

      LinkLabel lblLink = new LinkLabel("http://www.tinymediamanager.org");
      lblLink.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent arg0) {
          try {
            TmmUIHelper.browseUrl("http://www.tinymediamanager.org/index.php/changelog/");
          }
          catch (Exception e) {
          }
        }
      });
      panel.add(lblLink, "4, 2");

      JButton btnClose = new JButton(BUNDLE.getString("Button.close")); //$NON-NLS-1$
      btnClose.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent arg0) {
          setVisible(false);
        }
      });
      panel.add(btnClose, "8, 2");
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension superPref = super.getPreferredSize();
    return new Dimension((int) (700 > superPref.getWidth() ? superPref.getWidth() : 700), (int) (500 > superPref.getHeight() ? superPref.getHeight()
        : 500));
  }

  private String prepareTextAsHtml(String originalText) {
    Pattern pattern = Pattern.compile("(http[s]?://.*?)[ )]");
    Matcher matcher = pattern.matcher(originalText);
    while (matcher.find()) {
      originalText = originalText.replace(matcher.group(1), "<a href=\"" + matcher.group(1) + "\">" + matcher.group(1) + "</a>");
    }

    return "<html><pre>" + originalText + "</pre><html>";
  }
}
