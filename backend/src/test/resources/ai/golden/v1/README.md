# Golden AI evaluation dataset v1

This directory contains synthetic, non-private cases for the P7 explanation,
question-generation, and response-evaluation task contracts.

Deterministic rules check only explicit case facts such as required concepts,
forbidden claims, source-reference boundaries, requested contract values, and
classification acceptance rules. Passing those rules does not establish
medical or educational quality. The manual live-provider report must also be
reviewed by a qualified human using the rubrics in Document 15.

Provider/model/task evaluation results are evidence for a later approval
decision. This dataset and its runner never modify `evaluationApprovedTasks`.
