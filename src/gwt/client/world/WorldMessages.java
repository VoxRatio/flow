package client.world;


/**
 * Interface to represent the messages contained in resource  bundle:
 * 	/export/msoy/src/gwt/client/world/WorldMessages.properties'.
 */
public interface WorldMessages extends com.google.gwt.i18n.client.Messages {
  
  /**
   * Translated "That member could not be found.".
   * 
   * @return translated "That member could not be found."
   * @gwt.key noSuchMember
   */
  String noSuchMember();

  /**
   * Translated "Neighborhood".
   * 
   * @return translated "Neighborhood"
   * @gwt.key neighborhoodTitle
   */
  String neighborhoodTitle();

  /**
   * Translated "Popular Spots".
   * 
   * @return translated "Popular Spots"
   * @gwt.key hotSpotsTitle
   */
  String hotSpotsTitle();

  /**
   * Translated "temp".
   * 
   * @return translated "temp"
   * @gwt.key temp
   */
  String temp();

  /**
   * Translated "That neighborhood could not be found.".
   * 
   * @return translated "That neighborhood could not be found."
   * @gwt.key noSuchNeighborhood
   */
  String noSuchNeighborhood();
}
