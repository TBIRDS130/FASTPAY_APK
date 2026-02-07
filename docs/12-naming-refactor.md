# APK Component Naming Refactor

**Purpose:** Replace generic layout IDs (textView11, cardView6, etc.) with descriptive names for cards, UI, and animations. Reference for consistency when editing layouts and Kotlin code.

---

## Layout IDs: Old → New

### Root & Background
| Old | New |
|-----|-----|
| main | activationRootLayout |
| gridBackground | activationGridBackground |
| scanlineView | activationScanlineOverlay |
| color | activatedStateBackgroundOverlay |

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
| testingButton | activationModeTestingText |
| runningButton | activationModeRunningText |

### Input & Buttons
| Old | New |
|-----|-----|
| cardView6 | activationInputCard |
| editTextText2 | activationPhoneInput |
| cardView7 | activationActivateButton |
| textView3 | activationActivateButtonText |
| clearButton | activationClearButton |

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

### Activated State
| Old | New |
|-----|-----|
| activatedTopContainer | activatedStateTopContainer |
| phoneDisplayCard | activatedPhoneDisplayCard |
| bankTagCard | activatedBankTagCard |

### Instruction Card
| Old | New |
|-----|-----|
| insCard | activatedInstructionCard |
| textView13 | activatedInstructionTitle |
| emptyStateLayout | activatedInstructionEmptyState |
| textView14 | activatedInstructionContent |
| instructionImage | activatedInstructionImage |
| instructionVideo | activatedInstructionVideo |

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

## Note

Keep existing `transitionName` values (e.g. logo_transition, tagline_transition) – they are used for shared element transitions between activities and must match ActivatedActivity.
