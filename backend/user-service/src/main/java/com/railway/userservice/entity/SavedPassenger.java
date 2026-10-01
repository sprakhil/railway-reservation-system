package com.railway.userservice.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="saved_passengers",indexes=@Index(name="idx_saved_passenger_owner",columnList="username")) @Getter @Setter @NoArgsConstructor
public class SavedPassenger {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String username;
 @Column(nullable=false,length=100) private String name;
 @Column(nullable=false) private Integer age;
 @Column(nullable=false,length=10) private String gender;
 @Column(length=20) private String berthPreference;
 @Version private Long version;
}
