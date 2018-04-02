package eu.gity.jmeter.webgenerator;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JOptionPane;
import javax.swing.JTree;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.gui.action.AddToTree;
import org.apache.jmeter.gui.action.Copy;
import org.apache.jmeter.gui.action.Duplicate;
import org.apache.jmeter.gui.action.Paste;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;


/**
 * This is a singleton class used to guard copy/cut-paste and drag&drop user operations to disable copying or
 * of Web Generator
 * 
 * It's made final to improve performance because subclassing is not welcomed anyway as someone might try to create another instance (which
 * is defeated second time by a private ctor).
 * 
 * @author Gity a.s.
 *
 */
public final class WebGeneratorTreeGuard {
	
	private static final WebGeneratorTreeGuard INSTANCE = new WebGeneratorTreeGuard();
	
	private boolean listenersRegistered = false;
	
	/**
	 * Exists to defeat external instantiation (double-guarded together with declaring the class final)
	 */
	private WebGeneratorTreeGuard() {		
	}
	
	/**
	 * 
	 * @return the singleton instance of this class
	 */
	public static WebGeneratorTreeGuard getInstance() {
		return INSTANCE;
	}

	
	/**
	 * Register all necessary JMeter's ActionRouter listeners to guard the insertion events if not done yet.
	 * The purpose of the listeners is to prevent the user from adding more instances of WebGenerator to the tree
	 */
	public void registerListeners() {
		if (!listenersRegistered) {
			registerClipboardListeners();
			registerAddToTreeListener();
			registerDuplicateListener();
			listenersRegistered = true;
		}		
	}
	
	/**
	 * Reload tree after moving nodes programmatically and shows user an error message 
	 */
	private void reloadTreeAndShowError() {
		JTree jmeterTree = GuiPackage.getInstance().getMainFrame().getTree();
		((JMeterTreeModel)jmeterTree.getModel()).reload();
		for (int i = 0; i < jmeterTree.getRowCount(); i++) {
			jmeterTree.expandRow(i);
		}		
		
		JOptionPane.showMessageDialog(null, JMeterUtils.getResString("web_generator_error_insertion"),
				JMeterUtils.getResString("web_generator_error"), JOptionPane.ERROR_MESSAGE);		
	}
	
	
	
	/**
	 * Registration of listeners to manage copy/cut&paste user action.
	 * 
	 * Pre-action listener of "Cut" event saves reference to parent of the cut node which may be needed 
	 *  by following post-action listener of "Paste" event to revert the changes if they are not approved
	 */
	private void registerClipboardListeners() {
				
		ActionRouter.getInstance().addPostActionListener(Paste.class, new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
													
				JMeterTreeNode currentNode = GuiPackage.getInstance().getCurrentNode();				
				JMeterTreeNode[] movedNodes = Copy.getCopiedNodes();
				JMeterTreeModel jmeterTreeModel = GuiPackage.getInstance().getTreeModel();	
				boolean correction = false;
												
				//Excludes TestPlan and WorkBench as the event is also triggered for them even if they don't have these nodes as children
				if (currentNode.getParent() == null || currentNode.getParent() == jmeterTreeModel.getRoot()) {
					return;
				}
				
				for (JMeterTreeNode movedNode : movedNodes) {				
					
					TestElement movedNodeTE = movedNode.getTestElement();
					String guiClass = movedNodeTE.getPropertyAsString(TestElement.GUI_CLASS);
											
					if (guiClass.contains("WebGenerator")) {
						ArrayList<JMeterTreeNode> rcNodes = new ArrayList<JMeterTreeNode>(jmeterTreeModel.getNodesOfType(ResultCollector.class));
						int count = 0;
						for (JMeterTreeNode rcNode : rcNodes) {
							if (rcNode.getTestElement().getPropertyAsString(TestElement.GUI_CLASS).contains("WebGenerator")) {
								count++;
							}
							if (count > 1) { //Means "copy" was performed, not "cut"
								
								//Can not directly remove movedNode from it's new parent parent because it doesn't have any parent but 
								//new node is created from the original node TE so it can be found by comparing of original TE with TEs of children
								for (int i = 0; i < currentNode.getChildCount(); i++) {
									if (((JMeterTreeNode)currentNode.getChildAt(i)).getTestElement().equals(movedNodeTE)) {
										currentNode.remove(i);
										break;
									}
								}	
								correction = true;
								break;
							}
						}						
					}	
						
																		
				}
				if (correction) {
					reloadTreeAndShowError();
				}
			}
				
		});	
		
	}
	
	
	
	/**
	 * Registration of listener to manage "Duplicate" user action to prevent the user from adding
	 * more than one instance of the Web Generator to the JMeter tree via pop-up menu
	 */
	private void registerDuplicateListener() {
				
		ActionRouter.getInstance().addPostActionListener(Duplicate.class, new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JMeterTreeNode currentNode = GuiPackage.getInstance().getCurrentNode();					 
				JMeterTreeModel jmeterTreeModel = (JMeterTreeModel)GuiPackage.getInstance().getMainFrame().getTree().getModel();
				if (currentNode.getTestElement().getPropertyAsString(TestElement.GUI_CLASS).contains("WebGenerator")) {
					JMeterTreeNode parentNode = (JMeterTreeNode)currentNode.getParent();
					jmeterTreeModel.removeNodeFromParent(currentNode);
					jmeterTreeModel.reload(parentNode);
					JOptionPane.showMessageDialog(null, JMeterUtils.getResString("web_generator_error_insertion"),
							JMeterUtils.getResString("web_generator_error"), JOptionPane.ERROR_MESSAGE);					
				}				
			}	
							
		});
	}
	
	/**
	 * Registration of listener to catch an "AddToTree" event to prevent user from adding Web Generator to JMeter tree multiple times
	 * via pop-up menu   
	 */
	private void registerAddToTreeListener() {
	   	
		ActionRouter.getInstance().addPostActionListener(AddToTree.class, new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JMeterTreeModel jmeterTreeModel = ((JMeterTreeModel)GuiPackage.getInstance().getMainFrame().getTree().getModel());
				JMeterTreeNode currentNode = GuiPackage.getInstance().getCurrentNode();	
				ArrayList<JMeterTreeNode> rcNodes = new ArrayList<JMeterTreeNode>(jmeterTreeModel.getNodesOfType(ResultCollector.class));
				int count = 0;
				for (JMeterTreeNode rcNode : rcNodes) {
					if (rcNode.getTestElement().getPropertyAsString(TestElement.GUI_CLASS).contains("WebGenerator")) {
						count++;
					}
					if (count > 1) {
						JMeterTreeNode parentNode = (JMeterTreeNode)currentNode.getParent();
						jmeterTreeModel.removeNodeFromParent(currentNode);
						jmeterTreeModel.reload(parentNode);
						JOptionPane.showMessageDialog(null, JMeterUtils.getResString("web_generator_error_insertion"),
								JMeterUtils.getResString("web_generator_error"), JOptionPane.ERROR_MESSAGE);
						break;
					}
				}			
			}			
			
		});
									
   }
}
