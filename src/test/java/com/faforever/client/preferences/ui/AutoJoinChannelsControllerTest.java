package com.faforever.client.preferences.ui;

import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class AutoJoinChannelsControllerTest extends AbstractPlainJavaFxTest {

  private AutoJoinChannelsController instance;
  @Mock
  private PreferencesService preferenceService;
  @Mock
  private Preferences preferences;

  @Before
  public void setUp() throws Exception {
    preferences = new Preferences();
    when(preferenceService.getPreferences()).thenReturn(preferences);

    instance = new AutoJoinChannelsController(preferenceService);
    loadFxml("theme/settings/auto_join_channels.fxml", param -> instance);
  }


  @Test
  public void testOnAddChannelButtonPressed() throws Exception {
    instance.channelTextField.setText("#newbie");
    instance.onAddChannelButtonPressed();
    List<String> expected = Arrays.asList("#" + Locale.getDefault().getCountry(), "#newbie");
    assertThat(preferences.getChat().getAutoJoinChannels(), is(expected));
  }

  @Test
  public void testOnChannelSelectedAndRemoved() throws Exception {
    preferences.getChat().getAutoJoinChannels().clear();
    preferences.getChat().getAutoJoinChannels().add("#newbie");
    assertFalse(preferences.getChat().getAutoJoinChannels().isEmpty());
    assertFalse(instance.channelListView.getItems().isEmpty());

    instance.channelListView.getSelectionModel().select("#newbie");
    instance.removeButton.fire();

    assertTrue(preferences.getChat().getAutoJoinChannels().isEmpty());
    assertTrue(instance.channelListView.getItems().isEmpty());
  }

}
