package com.faforever.client.tutorial;

import com.faforever.client.remote.FafService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class TutorialService {
  private final FafService fafService;

  @Inject
  public TutorialService(FafService fafService) {
    this.fafService = fafService;
  }

  public CompletableFuture<List<TutorialCategory>> getTutorialCategories() {
    return fafService.getTutorialCategories();
  }
}
