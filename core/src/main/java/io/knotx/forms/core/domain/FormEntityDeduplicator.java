/*
 * Copyright (C) 2018 Knot.x Project
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
package io.knotx.forms.core.domain;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class FormEntityDeduplicator {

  private static final Logger LOGGER = LoggerFactory.getLogger(FormEntityDeduplicator.class);

  private FormEntityDeduplicator() {
    // util class
  }

  public static List<FormEntity> uniqueFormEntities(List<FormEntity> forms) {
    if (formIdentifiersAreNotUnique(forms)) {
      LOGGER.error("Form identifiers are not unique [{}]", forms.stream().map(FormEntity::identifier).toArray());
      Set<String> duplicates = findDuplicateIds(forms);
      markDuplicatesAsFailed(forms, duplicates);
      boolean fallbackDetected = verifyAllFailedFragmentsHaveFallback(forms);
      if (fallbackDetected) {
        forms = forms.stream()
            .filter(form -> !duplicates.contains(form.identifier()))
            .collect(Collectors.toList());
      } else {
        throw new FormConfigurationException("Form identifiers are not unique!", fallbackDetected);
      }
    }
    return forms;
  }

  private static boolean formIdentifiersAreNotUnique(List<FormEntity> forms) {
    return forms.size() != forms.stream().map(FormEntity::identifier).collect(Collectors.toSet()).size();
  }

  private static Set<String> findDuplicateIds(Collection<FormEntity> collection) {
    Set<String> uniques = new HashSet<String>();
    return collection.stream()
        .map(FormEntity::identifier)
        .filter(e -> !uniques.add(e))
        .collect(Collectors.toSet());
  }

  private static void markDuplicatesAsFailed(List<FormEntity> forms, Set<String> duplicateIds) {
    forms.stream()
        .filter(f -> duplicateIds.contains(f.identifier()))
        .forEach(FormEntityDeduplicator::markDuplicateAsFailed);
  }

  private static void markDuplicateAsFailed(FormEntity form) {
    String knotId = form.fragment().knots().stream()
        .filter(knot -> knot.startsWith(FormConstants.FRAGMENT_KNOT_PREFIX))
        .findFirst()
        .get();

    form.fragment().failure(knotId, new IllegalStateException(String.format("Duplicate form ID %s", form.identifier())));
  }

  private static boolean verifyAllFailedFragmentsHaveFallback(List<FormEntity> forms) {
    return forms.stream()
        .filter(f -> f.fragment().failed())
        .allMatch(f -> f.fragment().fallback().isPresent());
  }

}
