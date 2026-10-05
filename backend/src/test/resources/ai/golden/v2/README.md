# Golden AI evaluation dataset v2

This directory contains the complete synthetic, non-private Golden AI dataset
for the P7 explanation, question-generation, and response-evaluation task
contracts.

Version 2 corrects the construct validity of two P7-09 response-evaluation
fixtures: the partial-credit case now requires learner-demonstrated mechanism
knowledge, and the wrong-reasoning case now requires an independently retrieved
identification before testing its incorrect mechanism.

Version 1 remains retained unchanged as historical evaluation evidence. These
v2 corrections do not retroactively change any previous v1 provider/model run
or its reported result.

Deterministic rules check only explicit case facts such as required concepts,
forbidden claims, source-reference boundaries, requested contract values, and
classification acceptance rules. Passing those rules does not establish
medical or educational quality. Human review remains required using the
rubrics in Document 15.

Provider/model/task evaluation results are evidence for a later approval
decision. This dataset and its runner never modify `evaluationApprovedTasks`.
