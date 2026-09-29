package rent.history.checker.entity.constants;

public enum ReviewStatus {
	PENDING,
	APPROVED,
	REJECTED,
}
// TODO: On your Spring Boot side, ReviewStatus enum will need to exist and ReviewDto will need status + flatAddress
//  (or flatId + flat projection). I'll update MOCKS.md at the end.