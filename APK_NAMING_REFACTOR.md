# APK Component Naming Refactor

**Purpose:** Replace generic/opaque IDs (textView11, cardView6, etc.) with descriptive names for cards, UI, and animations.

---

## Layout IDs: Old → New

### Root & Background
| Old | New |
|-----|-----|
| main | activationRootLayout |
| gridBackground | activationGridBackground |
| scanlineView | activationScanlineOverlay |
| color | activatedStateBackgroundOverlay |

### Guidelines
| Old | New |
|-----|-----|
| logoPositionGuide | activationHeaderPositionGuide |
| inputPositionGuide | activationContentPositionGuide |

### Header
| Old | New |
|-----|-----|
| headerSection | activationHeaderSection |
| textView11 | activationLogoText |
| textView12 | activationTaglineText |

### Content
| Old | New |
|-----|-----|
| centerContentScroll | activationContentScrollView |
| centerContent | activationContentContainer |

### Form Card (Crypto Hash Card)
| Old | New |
|-----|-----|
| cryptoHashCardWrapper | activationFormCardOuterBorder |
| cryptoHashCard | activationFormCard |
| cryptoHashLabel | activationFormSecurityLabel |
| sha256Badge | activationFormHashBadge |

### Mode Selector
| Old | New |
|-----|-----|
| loginTypeSelector | activationModeSelector |
| testingButtonContainer | activationModeTestingContainer |
| testingButton | activationModeTestingText |
| runningButtonContainer | activationModeRunningContainer |
| runningButton | activationModeRunningText |

### Input & Buttons
| Old | New |
|-----|-----|
| cardView6 | activationInputCard |
| editTextText2 | activationPhoneInput |
| cardView7 | activationActivateButton |
| textView3 | activationActivateButtonText |
| clearButton | activationClearButton |
| clearButtonText | activationClearButtonText |

### Status Card (Activation Progress)
| Old | New |
|-----|-----|
| activationProgressOverlay | activationStatusCardContainer |
| activationProgressCard | activationStatusCard |
| activationProgressTitle | activationStatusCardTitle |
| activationProgressStatus | activationStatusCardStatusText |
| activationProgressSteps | activationStatusCardStepsContainer |
| activationStepValidate | activationStatusStepValidate |
| activationStepRegister | activationStatusStepRegister |
| activationStepSync | activationStatusStepSync |
| activationStepAuth | activationStatusStepAuth |
| activationStepResult | activationStatusStepResult |
| activationRetryContainer | activationRetryContainer |
| activationRetryStatus | activationRetryStatusText |
| activationRetryNow | activationRetryButton |

### Activated State (shown after success)
| Old | New |
|-----|-----|
| activatedTopContainer | activatedStateTopContainer |
| phoneDisplayCard | activatedPhoneDisplayCard |
| phoneDisplayText | activatedPhoneDisplayText |
| phoneDisplayAnimatedBorder | activatedPhoneDisplayAnimatedBorder |
| bankTagCard | activatedBankTagCard |
| statusLabel | activatedBankTagStatusLabel |
| bankTagText | activatedBankTagText |
| bankTagAnimatedBorder | activatedBankTagAnimatedBorder |

### Instruction Card
| Old | New |
|-----|-----|
| insCard | activatedInstructionCard |
| textView13 | activatedInstructionTitle |
| divider | activatedInstructionDivider |
| emptyStateLayout | activatedInstructionEmptyState |
| emptyStateIcon | activatedInstructionEmptyIcon |
| emptyStateTitle | activatedInstructionEmptyTitle |
| emptyStateMessage | activatedInstructionEmptyMessage |
| textView14 | activatedInstructionContent |
| instructionImage | activatedInstructionImage |
| instructionVideo | activatedInstructionVideo |
| textView19 | activatedUploadFileButton |
| instructionAnimatedBorder | activatedInstructionAnimatedBorder |

### Overlays
| Old | New |
|-----|-----|
| progressBar | activationLoadingOverlay |
| activationPromptOverlay | activationPermissionPromptOverlay |
| activationPromptCardInclude | activationPermissionPromptCard |

---

## Kotlin: Animation Function Names

| Old | New |
|-----|-----|
| animateButtonPress | animateActivateButtonPress |
| shakeView | shakeInputCard |
| setupInputFieldAnimation | setupPhoneInputFocusAnimation |
| animateHintText | animatePhoneInputHintTyping |
| startStatusTypingSequence | startActivationStatusTypingAnimation |
| startActivationButtonsAnimation | animateActivationButtonsLoading |
| showProgressOverlayAnimated | showActivationStatusCardWithFade |
| hideProgressOverlayAnimated | hideActivationStatusCardWithFade |

---

## Note on transitionName

Keep existing transition names (logo_transition, tagline_transition, etc.) - they are used for shared element transitions between activities and must match ActivatedActivity.
