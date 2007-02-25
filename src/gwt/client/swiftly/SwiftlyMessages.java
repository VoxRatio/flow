package client.swiftly;


/**
 * Interface to represent the messages contained in resource  bundle:
 * 	/export/msoy/src/gwt/client/swiftly/SwiftlyMessages.properties'.
 */
public interface SwiftlyMessages extends com.google.gwt.i18n.client.Messages {
  
  /**
   * Translated "Log in to view your Swiftly projects.".
   * 
   * @return translated "Log in to view your Swiftly projects."
   * @gwt.key indexLogon
   */
  String indexLogon();

  /**
   * Translated "Not a valid projectId: {0}".
   * 
   * @return translated "Not a valid projectId: {0}."
   * @gwt.key invalidProjectId
   */
  String invalidProjectId(String arg0);

  /**
   * Translated "No projects found.".
   * 
   * @return translated "No projects found."
   * @gwt.key noProjects
   */
  String noProjects();

  /**
   * Translated "No project types found.".
   * 
   * @return translated "No project types found."
   * @gwt.key noProjects
   */
  String noTypes();

  /**
   * Translated "Create Project.".
   * 
   * @return translated "Create Project."
   * @gwt.key createProject
   */
  String createProject();

  /**
   * Translated "Start a project!"
   * 
   * @return translated "Start a project!"
   * @gwt.key startProject
   */
  String startProject();

  /**
   * Translated "Project name"
   * 
   * @return translated "Project name"
   * @gwt.key projectName
   */
  String projectName();

  /**
   * Translated "Remixable?"
   * 
   * @return translated "Remixable?"
   * @gwt.key remixable
   */
  String remixable();

  /**
   * Translated "Your projects:".
   * 
   * @return translated "Your projects:"
   * @gwt.key membersProjects
   */
  String membersProjects();

  /**
   * Translated "Remixable projects:".
   * 
   * @return translated "Remixable projects:"
   * @gwt.key remixableProjects
   */
  String remixableProjects();

  /**
   * Translated "What type of project is this?".
   * 
   * @return translated "What type of project is this?"
   * @gwt.key selectType
   */
  String selectType();

  /**
   * Translated "You are Swiftly editing:".
   * 
   * @return translated "You are Swiftly editing:"
   * @gwt.key swiftlyEditing
   */
  String swiftlyEditing();
}
