# Agent Instructions

## Test Naming

- JUnit `@Test` method names must be written in Korean.
- Use underscores instead of spaces so Gradle and IDE test lists are readable.
- Example: `void 상품_등록에_성공한다() throws Exception`
- Do not add English camelCase test method names for new tests.
- Helper methods that are not test cases may stay in English camelCase.

## Backend Execution

- The backend lives in `backend`.
- Local `bootRun` requires `JWT_SECRET` unless `application.yaml` provides a local default.
- Recommended test command:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```
