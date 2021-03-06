package com.faforever.client.i18n;

import com.faforever.client.preferences.PreferencesService;
import com.google.common.base.Strings;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class I18n {
  private static final Pattern MESSAGES_FILE_PATTERN = Pattern.compile("(.*[/\\\\]messages)(?:_([a-z]{2}))(?:_([a-z]{2}))?\\.properties", Pattern.CASE_INSENSITIVE);
  private final ReloadableResourceBundleMessageSource messageSource;
  private final PreferencesService preferencesService;
  private final ObservableList<Locale> availableLanguages;

  private Locale userSpecificLocale;

  @Inject
  public I18n(ReloadableResourceBundleMessageSource messageSource, PreferencesService preferencesService) {
    this.messageSource = messageSource;
    this.preferencesService = preferencesService;
    availableLanguages = FXCollections.observableArrayList();
  }

  @PostConstruct
  public void postConstruct() throws IOException {
    Locale locale = preferencesService.getPreferences().getLocalization().getLanguage();
    if (locale != null) {
      userSpecificLocale = new Locale(locale.getLanguage(), locale.getCountry());
    } else {
      userSpecificLocale = Locale.getDefault();
    }

    loadAvailableLanguages();
  }

  private void loadAvailableLanguages() throws IOException {
    // These are the default languages shipped with the client
    availableLanguages.addAll(
        Locale.US,
        new Locale("cs"),
        Locale.GERMAN,
        Locale.FRENCH,
        new Locale("ru"),
        Locale.CHINESE
    );

    Path languagesDirectory = preferencesService.getLanguagesDirectory();
    if (Files.notExists(languagesDirectory)) {
      return;
    }

    try (Stream<Path> dir = Files.list(languagesDirectory)) {
      dir
          .map(path -> MESSAGES_FILE_PATTERN.matcher(path.toString()))
          .filter(Matcher::matches)
          .forEach(matcher -> {
            messageSource.addBasenames(Paths.get(matcher.group(1)).toUri().toString());

            availableLanguages.add(new Locale(matcher.group(2), Strings.nullToEmpty(matcher.group(3))));
          });
    }
  }


  public String get(String key, Object... args) {
    return get(userSpecificLocale, key, args);
  }

  public String get(Locale locale, String key, Object... args) {
    return messageSource.getMessage(key, args, locale);
  }


  public Locale getUserSpecificLocale() {
    return this.userSpecificLocale;
  }


  public String getQuantized(String singularKey, String pluralKey, long arg) {
    Object[] args = {arg};
    if (Math.abs(arg) == 1) {
      return messageSource.getMessage(singularKey, args, userSpecificLocale);
    }
    return messageSource.getMessage(pluralKey, args, userSpecificLocale);
  }


  public String number(int number) {
    return String.format(userSpecificLocale, "%d", number);
  }


  public String numberWithSign(int number) {
    return String.format(userSpecificLocale, "%+d", number);
  }


  public String number(double number) {
    return String.format(userSpecificLocale, "%f", number);
  }


  public String rounded(double number, int digits) {
    return String.format(userSpecificLocale, "%." + digits + "f", number);
  }

  public ReadOnlyListWrapper<Locale> getAvailableLanguages() {
    return new ReadOnlyListWrapper<>(availableLanguages);
  }
}
