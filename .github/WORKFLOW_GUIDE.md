# Workflow Guide

This repository uses a simple Test-to-master pipeline.

## Branches

- `Test`: checkpoint branch for active work and experiments.
- `master`: stable branch for code that has passed CI and been approved.

## Workflow Files

1. `.github/workflows/01-ci.yml`
   - Runs on pushes to `Test`.
   - Runs on pull requests targeting `master`.
   - Builds/tests the project.

2. `.github/workflows/02-open-test-pr.yml`
   - Runs after a push to `Test`.
   - Opens a `Test` -> `master` pull request if `Test` is ahead and one is not already open.

3. `.github/workflows/03-merge-gate.yml`
   - Watches the `Test` -> `master` pull request.
   - Squash-merges after CI passes and `Samy109` approves the PR.

4. `.github/workflows/04-pr-age-alert.yml`
   - Runs daily.
   - Creates one assigned `workflow-alert` issue if the `Test` -> `master` PR stays open longer than 3 days.

## Normal Use

1. Commit and push work to `Test`.
2. Wait for CI to finish.
3. Review the automatically created pull request.
4. Approve the PR when it is ready.
5. The merge gate merges it into `master`.

## Notifications

For CI failure emails, use GitHub's personal notification setting:

- Settings -> Notifications -> System -> Actions
- Choose Email
- Enable failed workflow notifications only

The 3-day PR alert is handled by `04-pr-age-alert.yml` by creating an assigned GitHub issue.
